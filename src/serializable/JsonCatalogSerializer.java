package serializable;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import model.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class JsonCatalogSerializer implements Serializer<List<CatalogItem>> {

    public static final int SCHEMA_VERSION = 1;

    private static final String FIELD_SCHEMA_VERSION = "schema_version";
    private static final String FIELD_ITEMS = "items";

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String format() {
        return "json";
    }

    public int getFieldSchemaVersion() {
        return SCHEMA_VERSION;
    }

    private static JsonNode required(JsonNode node, String key) throws IOException {
        JsonNode value = node.get(key);
        if (value == null || value.isNull())
            throw new IOException("Отсутствует поле: " + key);
        return value;
    }

    private static String string(JsonNode node, String key) throws IOException {
        JsonNode value = required(node, key);
        if (!value.isTextual())
            throw new IOException("Ожидается строка: " + key);
        return value.textValue();
    }

    private static int integer(JsonNode node, String key) throws IOException {
        JsonNode value = required(node, key);
        if (!value.isIntegralNumber() || !value.canConvertToInt())
            throw new IOException("Ожидается int: " + key);
        return value.intValue();
    }

    @Override
    public void save(List<CatalogItem> data, Path file) throws IOException {
        List<CatalogItem> items = CatalogCodec.checked(data);

        ObjectNode root = mapper.createObjectNode();
        root.put(FIELD_SCHEMA_VERSION, SCHEMA_VERSION);
        ArrayNode array = root.putArray(FIELD_ITEMS);

        for (CatalogItem item : items) {
            CatalogCodec.checkCancelled();

            ObjectNode node = array.addObject();
            node.put("type", item.getType().getCsvName());
            node.put("itemNumber", item.getItemNumber());
            node.put("productName", item.getProductName());
            node.put("category", item.getCategory());
            node.put("price", item.getPrice());
            node.put("remainder", item.getRemainder());

            if (item instanceof ProductWithWarranty warranty) {
                node.put("startOfWarranty", warranty.getStartOfWarranty().toString());
                node.put("warrantyMonths", warranty.getWarrantyMonths());
            }
        }

        Path target = file.toAbsolutePath();
        Path temp = Files.createTempFile(target.getParent(), ".catalog-", ".tmp");
        try {
            try (OutputStream out = new FilterOutputStream(Files.newOutputStream(temp)) {
                @Override public void write(int b) throws IOException {
                    CatalogCodec.checkCancelled();
                    this.out.write(b);
                }
                @Override public void write(byte[] bytes, int off, int len) throws IOException {
                    CatalogCodec.checkCancelled();
                    this.out.write(bytes, off, len);
                }
            }) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(out, root);
            }

            CatalogCodec.checkCancelled();
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            throw new IOException("Атомарное сохранение не поддерживается", ex);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Override
    public List<CatalogItem> load(Path file) throws IOException {
        if (Files.size(file) > CatalogCodec.MAX_FILE_BYTES)
            throw new IOException("JSON превышает лимит 256 МиБ");

        List<CatalogItem> result = new ArrayList<>();
        Map<Integer, Integer> keyLines = new HashMap<>();
        boolean foundItems = false;

        try (InputStream raw = new InterruptibleInputStream(
                new BoundedInputStream(Files.newInputStream(file), CatalogCodec.MAX_FILE_BYTES));
             JsonParser parser = mapper.getFactory().createParser(raw)) {

            if (parser.nextToken() != JsonToken.START_OBJECT)
                throw new IOException("Некорректный корень JSON: ожидался объект (строка " + lineOf(parser) + ")");

            JsonToken token;
            while ((token = parser.nextToken()) != null) {
                if (token == JsonToken.END_OBJECT)
                    break;

                if (token != JsonToken.FIELD_NAME)
                    throw new IOException("Некорректный JSON: ожидалось имя поля (строка " + lineOf(parser) + ")");

                String fieldName = parser.currentName();
                JsonToken valueToken = parser.nextToken();

                if (FIELD_ITEMS.equals(fieldName)) {
                    foundItems = true;
                    if (valueToken != JsonToken.START_ARRAY)
                        throw new IOException("Поле " + FIELD_ITEMS
                                + " должно быть массивом (строка " + lineOf(parser) + ")");
                    readItems(parser, result, keyLines);

                } else if (FIELD_SCHEMA_VERSION.equals(fieldName)) {
                    if (valueToken != JsonToken.VALUE_NUMBER_INT)
                        throw new IOException("Поле " + FIELD_SCHEMA_VERSION
                                + " должно быть целым числом (строка " + lineOf(parser) + ")");

                    int version = parser.getIntValue();
                    validateSchemaVersion(version);

                } else {
                    parser.skipChildren();
                }
            }

            if (!foundItems)
                throw new IOException("Отсутствует поле: " + FIELD_ITEMS);

            if (parser.nextToken() != null)
                throw new IOException("Лишние данные после корневого объекта (строка "
                        + lineOf(parser) + ")");

        } catch (JsonProcessingException ex) {
            throw jsonError(ex);
        }

        return List.copyOf(result);
    }

    private static void validateSchemaVersion(int version) throws IOException {
        if (version < 1)
            throw new IOException("Некорректная версия схемы JSON: schema_version=" + version);

        if (version > SCHEMA_VERSION)
            throw new IOException("JSON-файл создан в более новой версии приложения: "
                    + "schema_version=" + version + " > " + SCHEMA_VERSION
                    + ". Обновите приложение или сохраните файл в текущей версии.");

        if (version < SCHEMA_VERSION)
            throw new IOException("JSON-файл версии " + version
                    + " не поддерживается текущей версией (ожидается " + SCHEMA_VERSION
                    + "). Требуется миграция.");
    }

    /** Читает массив items по одному элементу, отслеживая строку каждого */
    private void readItems(JsonParser parser, List<CatalogItem> result,
                           Map<Integer, Integer> keyLines) throws IOException {
        JsonToken token;
        while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
            if (token == null)
                throw new IOException("Неожиданный конец файла в массиве items");

            int line = lineOf(parser);
            JsonNode node = mapper.readTree(parser);
            try {
                CatalogItem item = parseItem(node);
                Integer prev = keyLines.putIfAbsent(item.getItemNumber(), line);
                if (prev != null)
                    throw new IOException("повтор артикула " + item.getItemNumber()
                            + " (первое вхождение в строке " + prev + ")");
                if (result.size() >= CatalogCodec.MAX_ITEMS)
                    throw new IOException("Превышен лимит " + CatalogCodec.MAX_ITEMS + " записей");
                result.add(item);
            } catch (IOException ex) {
                throw new IOException("Строка " + line + ": " + ex.getMessage(), ex);
            }
        }
    }

    private static final Set<String> KNOWN_ITEM_FIELDS = Set.of(
            "type", "itemNumber", "productName", "category",
            "price", "remainder", "startOfWarranty", "warrantyMonths");

    private CatalogItem parseItem(JsonNode node) throws IOException {
        if (!node.isObject())
            throw new IOException("Запись должна быть объектом JSON");

        Iterator<String> it = node.fieldNames();
        while (it.hasNext()) {
            String name = it.next();
            if (!KNOWN_ITEM_FIELDS.contains(name))
                throw new IOException("Неизвестное поле в записи: \"" + name + "\"");
        }

        String type = node.has("type") ? string(node, "type") : "Product";
        String start = "Warranty".equals(type) ? string(node, "startOfWarranty") : null;
        Integer months = "Warranty".equals(type) ? integer(node, "warrantyMonths") : null;

        return CatalogCodec.create(
                type, integer(node, "itemNumber"), string(node, "productName"),
                string(node, "category"), integer(node, "price"),
                integer(node, "remainder"), start, months);
    }

    private static int lineOf(JsonParser parser) {
        JsonLocation loc = parser.currentLocation();
        return loc == null ? -1 : loc.getLineNr();
    }

    private static IOException jsonError(JsonProcessingException ex) {
        JsonLocation loc = ex.getLocation();
        String msg = loc != null
                ? "Ошибка разбора JSON (строка " + loc.getLineNr()
                + ", колонка " + loc.getColumnNr() + "): " + ex.getOriginalMessage()
                : "Ошибка разбора JSON: " + ex.getOriginalMessage();
        return new IOException(msg, ex);
    }
}