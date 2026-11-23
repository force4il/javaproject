package src.serializable;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import src.exceptions.MigrationException;
import src.model.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class JsonCatalogSerializer
        implements Serializer<List<CatalogItem>> {

    private static final String FIELD_SCHEMA_VERSION = "schema_version";
    private static final String FIELD_ITEMS = "items";

    private static final Set<String> BASE_ITEM_FIELDS = Set.of("type",
            "itemNumber", "productName", "category", "price", "remainder");

    private static final Set<String> WARRANTY_EXTRA = Set.of("startOfWarranty", "warrantyMonths");
    private static final Set<String> V2_EXTRA = Set.of("supplier", "countryOfOrigin");
    private static final Set<String> V3_EXTRA = Set.of("discountPercent", "manufactureDate");

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String format() {
        return "json";
    }

    @Override
    public int currentVersion() {
        return CatalogVersions.CURRENT;
    }

    @Override
    public void save(List<CatalogItem> data, Path file, int targetVersion) throws IOException {
        CatalogVersions.validateSupported(targetVersion);

        List<CatalogItem> items = CatalogCodec.checked(data);
        ObjectNode root = mapper.createObjectNode();
        root.put(FIELD_SCHEMA_VERSION, targetVersion);
        ArrayNode array = root.putArray(FIELD_ITEMS);
        for (CatalogItem item : items) {
            CatalogCodec.checkCancelled();

            writeItem(array.addObject(), item, targetVersion);
        }

        Path target = file.toAbsolutePath();
        Path temp = Files.createTempFile(target.getParent(), ".catalog-", ".tmp");
        try {
            try (OutputStream out = new FilterOutputStream(
                    Files.newOutputStream(temp)
            ) {
                @Override
                public void write(int b) throws IOException {
                    CatalogCodec.checkCancelled();
                    this.out.write(b);
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    CatalogCodec.checkCancelled();
                    this.out.write(b, off, len);
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

    private void writeItem(ObjectNode node, CatalogItem item, int version) {
        node.put("type", item.getType().getCsvName());
        node.put("itemNumber", item.getItemNumber());
        node.put("productName", item.getProductName());
        node.put("category", item.getCategory());
        node.put("price", item.getPrice());
        node.put("remainder", item.getRemainder());

        if (item instanceof ProductWithWarranty w) {
            if (w.getStartOfWarranty() != null)
                node.put("startOfWarranty", w.getStartOfWarranty().toString());
            else
                node.putNull("startOfWarranty");

            node.put("warrantyMonths", w.getWarrantyMonths());
        }

        if (version >= CatalogVersions.V2 && item instanceof Product p) {
            node.put("supplier", p.getSupplier() == null ? "" : p.getSupplier());
            node.put("countryOfOrigin", p.getCountryOfOrigin() == null ? "" : p.getCountryOfOrigin());
        }

        if (version >= CatalogVersions.V3 && item instanceof Product p) {
            node.put("discountPercent", p.getDiscountPercent());
            node.put("manufactureDate", (p.getManufactureDate() == null
                    ? Product.DEFAULT_MANUFACTURE_DATE : p.getManufactureDate()).toString());
        }
    }

    @Override
    public String describeDowngradeLoss(int targetVersion) {
        return CatalogVersions.lossFromTo(currentVersion(), targetVersion);
    }

    @Override
    public LoadResult<List<CatalogItem>> load(Path file) throws IOException {
        if (Files.size(file) > CatalogCodec.MAX_FILE_BYTES) {
            throw new MigrationException(MigrationException.Reason.CORRUPTED,
                    "JSON превышает лимит 256 МиБ");
        }

        List<CatalogItem> result = new ArrayList<>();
        Map<Integer, Integer> keyLines = new HashMap<>();

        boolean foundItems = false;
        boolean foundVersion = false;
        int sourceVersion = 0;
        try (InputStream raw = new InterruptibleInputStream(new BoundedInputStream(
                Files.newInputStream(file), CatalogCodec.MAX_FILE_BYTES));
                JsonParser parser = mapper.getFactory().createParser(raw)) {

            if (parser.nextToken() != JsonToken.START_OBJECT)
                throw new MigrationException(MigrationException.Reason.CORRUPTED,
                        "Некорректный корень JSON (строка " + lineOf(parser) + ")");

            JsonToken token;
            while ((token = parser.nextToken()) != null) {
                if (token == JsonToken.END_OBJECT)
                    break;

                if (token != JsonToken.FIELD_NAME)
                    throw new MigrationException(MigrationException.Reason.CORRUPTED,
                            "Ожидалось имя поля (строка " + lineOf(parser) + ")");

                String fieldName = parser.currentName();
                JsonToken valueToken = parser.nextToken();
                if (valueToken == null)
                    throw new MigrationException(MigrationException.Reason.CORRUPTED,
                            "Неожиданный конец JSON");

                if (FIELD_SCHEMA_VERSION.equals(fieldName)) {
                    if (foundVersion)
                        throw new MigrationException(MigrationException.Reason.CORRUPTED,
                                "Поле schema_version указано несколько раз");

                    if (valueToken != JsonToken.VALUE_NUMBER_INT)
                        throw new MigrationException(MigrationException.Reason.BAD_FIELD,
                                "schema_version должен быть целым числом");

                    sourceVersion = parser.getIntValue();
                    CatalogVersions.validateSupported(sourceVersion);
                    foundVersion = true;
                } else if (FIELD_ITEMS.equals(fieldName)) {
                    if (!foundVersion)
                        throw new MigrationException(MigrationException.Reason.CORRUPTED,
                                "Отсутствует schema_version перед items");

                    if (foundItems)
                        throw new MigrationException(MigrationException.Reason.CORRUPTED,
                                "Поле items указано несколько раз");

                    if (valueToken != JsonToken.START_ARRAY)
                        throw new MigrationException(MigrationException.Reason.BAD_FIELD,
                                "items должен быть массивом");

                    foundItems = true;
                    readItems(parser, result, keyLines, sourceVersion);
                } else {
                    throw new MigrationException(MigrationException.Reason.CORRUPTED,
                            "Неизвестное поле корневого объекта: " + fieldName);
                }
            }

            if (!foundVersion)
                throw new MigrationException(MigrationException.Reason.CORRUPTED,
                        "Отсутствует поле: " + FIELD_SCHEMA_VERSION);

            if (!foundItems)
                throw new MigrationException(MigrationException.Reason.CORRUPTED,
                        "Отсутствует поле: " + FIELD_ITEMS);

            if (token != JsonToken.END_OBJECT)
                throw new MigrationException(MigrationException.Reason.CORRUPTED,
                        "Неожиданный конец корневого объекта JSON");

            if (parser.nextToken() != null)
                throw new MigrationException(MigrationException.Reason.CORRUPTED,
                        "Лишние данные после корневого объекта");

        } catch (MigrationException ex) {
            throw ex;
        } catch (JsonProcessingException ex) {
            throw jsonError(ex);
        }

        return new LoadResult<>(List.copyOf(result), sourceVersion, CatalogVersions.CURRENT);
    }

    private void readItems(JsonParser parser, List<CatalogItem> result,
            Map<Integer, Integer> keyLines, int sourceVersion) throws IOException {
        JsonToken token;
        while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
            CatalogCodec.checkCancelled();
            if (token == null)
                throw new MigrationException(MigrationException.Reason.CORRUPTED,
                        "Неожиданный конец файла в массиве items");

            int line = lineOf(parser);
            if (result.size() >= CatalogCodec.MAX_ITEMS)
                throw new MigrationException(MigrationException.Reason.CORRUPTED,
                        "Превышен лимит " + CatalogCodec.MAX_ITEMS + " записей");

            JsonNode node = mapper.readTree(parser);
            try {
                CatalogItem item = parseItem(node, sourceVersion);
                Integer previousLine = keyLines.putIfAbsent(item.getItemNumber(), line);
                if (previousLine != null)
                    throw new MigrationException(MigrationException.Reason.CORRUPTED, "Повтор артикула "
                            + item.getItemNumber() + " (первое вхождение в строке " + previousLine + ")");

                result.add(item);
            } catch (MigrationException ex) {
                throw new MigrationException(ex.getReason(), "Строка " + line + ": " + ex.getMessage(), ex);
            } catch (IOException ex) {
                throw new MigrationException(MigrationException.Reason.BAD_FIELD,
                        "Строка " + line + ": " + ex.getMessage(), ex);
            }
        }
    }

    private CatalogItem parseItem(JsonNode node, int sourceVersion) throws IOException {
        if (node == null || !node.isObject())
            throw new MigrationException(MigrationException.Reason.CORRUPTED,
                    "Запись должна быть объектом JSON");

        String type = node.has("type") ? string(node, "type") : "Product";
        validateItemFields(node, type, sourceVersion);
        CatalogRecord r = new CatalogRecord();
        r.type = type;
        r.itemNumber = integer(node, "itemNumber");
        r.productName = string(node, "productName");
        r.category = string(node, "category");
        r.price = integer(node, "price");
        r.remainder = integer(node, "remainder");

        if ("Warranty".equals(type)) {
            r.startOfWarranty = string(node, "startOfWarranty");
            r.warrantyMonths = integer(node, "warrantyMonths");
        }

        if (sourceVersion >= CatalogVersions.V2 && !"Discontinued".equals(type)) {
            r.supplier = optionalString(node, "supplier", "");
            r.countryOfOrigin = optionalString(node, "countryOfOrigin", "");
        }

        if (sourceVersion >= CatalogVersions.V3 && !"Discontinued".equals(type)) {
            r.discountPercent = optionalInt(node, "discountPercent", 0);
            r.manufactureDate = optionalString(node, "manufactureDate", Product.DEFAULT_MANUFACTURE_DATE.toString());
        }

        CatalogMigrations.migrateToCurrent(r, sourceVersion);
        try {
            return CatalogMigrations.toItem(r);
        } catch (MigrationException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new MigrationException(MigrationException.Reason.BAD_FIELD,
                    "Ошибка миграции v" + sourceVersion + " -> v" + CatalogVersions.CURRENT
                            + " (артикул " + r.itemNumber + "): " + ex.getMessage(), ex);
        }
    }

    private static void validateItemFields(JsonNode node, String type, int sourceVersion) throws IOException {
        if (!Set.of("Product", "Warranty", "Discontinued").contains(type))
            throw new MigrationException(MigrationException.Reason.BAD_FIELD,
                    "Неизвестный тип товара: " + type);

        Set<String> allowed = new HashSet<>(BASE_ITEM_FIELDS);
        if ("Warranty".equals(type))
            allowed.addAll(WARRANTY_EXTRA);
        if (sourceVersion >= CatalogVersions.V2 && !"Discontinued".equals(type))
            allowed.addAll(V2_EXTRA);
        if (sourceVersion >= CatalogVersions.V3 && !"Discontinued".equals(type))
            allowed.addAll(V3_EXTRA);

        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            String name = fields.next();
            if (!allowed.contains(name))
                throw new MigrationException(MigrationException.Reason.BAD_FIELD,
                        "Неизвестное поле в записи: \"" + name + "\" (схема v" + sourceVersion + ")");
        }
    }


    private static JsonNode required(JsonNode node, String key) throws IOException {
        JsonNode value = node.get(key);
        if (value == null || value.isNull())
            throw new MigrationException(MigrationException.Reason.BAD_FIELD,
                    "Отсутствует поле: " + key);

        return value;
    }

    private static String string(JsonNode node, String key) throws IOException {
        JsonNode value = required(node, key);
        if (!value.isTextual())
            throw new MigrationException(MigrationException.Reason.BAD_FIELD,
                    "Ожидается строка: " + key);

        return value.textValue();
    }

    private static String optionalString(JsonNode node, String key, String defaultValue) throws IOException {
        JsonNode value = node.get(key);

        if (value == null || value.isNull())
            return defaultValue;
        if (!value.isTextual())
            throw new MigrationException(MigrationException.Reason.BAD_FIELD,
                    "Поле " + key + " должно быть строкой");

        return value.textValue();
    }

    private static int integer(JsonNode node, String key) throws IOException {
        JsonNode value = required(node, key);

        if (!value.isIntegralNumber() || !value.canConvertToInt())
            throw new MigrationException(MigrationException.Reason.BAD_FIELD,
                    "Ожидается int: " + key);

        return value.intValue();
    }

    private static int optionalInt(JsonNode node, String key, int defaultValue) throws IOException {
        JsonNode value = node.get(key);

        if (value == null || value.isNull())
            return defaultValue;
        if (!value.isIntegralNumber() || !value.canConvertToInt())
            throw new MigrationException(MigrationException.Reason.BAD_FIELD,
                    "Поле " + key + " должно быть int");

        return value.intValue();
    }

    private static int lineOf(JsonParser parser) {
        JsonLocation location = parser.currentLocation();
        return location == null ? -1 : location.getLineNr();
    }

    private static MigrationException jsonError(JsonProcessingException ex) {
        JsonLocation location = ex.getLocation();
        String message = location != null ? "Ошибка разбора JSON (строка "
                + location.getLineNr() + ", колонка " + location.getColumnNr() + "): "
                + ex.getOriginalMessage() : "Ошибка разбора JSON: " + ex.getOriginalMessage();
        return new MigrationException(MigrationException.Reason.CORRUPTED, message, ex);
    }

}
