package serializable;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import model.*;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

public final class JsonCatalogSerializer implements Serializer<List<CatalogItem>> {

    private static final String FIELD_SCHEMA_VERSION = "schema_version";
    private static final String FIELD_ITEMS = "items";

    private static final Set<String> BASE_ITEM_FIELDS = Set.of("type", "itemNumber", "productName", "category", "price", "remainder");
    private static final Set<String> WARRANTY_EXTRA = Set.of("startOfWarranty", "warrantyMonths");
    private static final Set<String> V2_EXTRA = Set.of("supplier", "countryOfOrigin");
    private static final Set<String> V3_EXTRA = Set.of("discountPercent", "manufactureDate");

    private final ObjectMapper mapper = new ObjectMapper();

    @Override public String format() { return "json"; }
    @Override public int currentVersion() { return CatalogVersions.CURRENT; }

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
            try (OutputStream out = new FilterOutputStream(Files.newOutputStream(temp)) {
                @Override public void write(int b) throws IOException {
                    CatalogCodec.checkCancelled(); this.out.write(b);
                }
                @Override public void write(byte[] b, int off, int len) throws IOException {
                    CatalogCodec.checkCancelled(); this.out.write(b, off, len);
                }
            }) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(out, root);
            }
            CatalogCodec.checkCancelled();
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
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
            node.put("startOfWarranty", w.getStartOfWarranty().toString());
            node.put("warrantyMonths", w.getWarrantyMonths());
        }
        if (version >= CatalogVersions.V2 && item instanceof Product p) {
            node.put("supplier", p.getSupplier() == null ? "" : p.getSupplier());
            node.put("countryOfOrigin", p.getCountryOfOrigin() == null ? "" : p.getCountryOfOrigin());
        }
        if (version >= CatalogVersions.V3 && item instanceof Product p) {
            node.put("discountPercent", p.getDiscountPercent());
            node.put("manufactureDate", p.getManufactureDate().toString());
        }
    }

    @Override
    public String describeDowngradeLoss(int targetVersion) {
        return CatalogVersions.lossFromTo(currentVersion(), targetVersion);
    }

    // ---------- LOAD (с миграцией) ----------
    @Override
    public LoadResult<List<CatalogItem>> load(Path file) throws IOException {
        if (Files.size(file) > CatalogCodec.MAX_FILE_BYTES)
            throw new IOException("JSON превышает лимит 256 МиБ");

        List<CatalogItem> result = new ArrayList<>();
        Map<Integer, Integer> keyLines = new HashMap<>();
        boolean foundItems = false;
        int sourceVersion = CatalogVersions.V1;

        try (InputStream raw = new InterruptibleInputStream(
                new BoundedInputStream(Files.newInputStream(file), CatalogCodec.MAX_FILE_BYTES));
             JsonParser parser = mapper.getFactory().createParser(raw)) {

            if (parser.nextToken() != JsonToken.START_OBJECT)
                throw new IOException("Некорректный корень JSON (строка " + lineOf(parser) + ")");

            JsonToken token;
            while ((token = parser.nextToken()) != null) {
                if (token == JsonToken.END_OBJECT) break;
                if (token != JsonToken.FIELD_NAME)
                    throw new IOException("Ожидалось имя поля (строка " + lineOf(parser) + ")");

                String fieldName = parser.currentName();
                JsonToken valueToken = parser.nextToken();

                if (FIELD_SCHEMA_VERSION.equals(fieldName)) {
                    if (foundItems)
                        throw new IOException("schema_version должен идти до items");
                    if (valueToken != JsonToken.VALUE_NUMBER_INT)
                        throw new IOException("schema_version должен быть целым числом");
                    sourceVersion = parser.getIntValue();
                    CatalogVersions.validateSupported(sourceVersion);
                } else if (FIELD_ITEMS.equals(fieldName)) {
                    foundItems = true;
                    if (valueToken != JsonToken.START_ARRAY)
                        throw new IOException("items должен быть массивом");
                    readItems(parser, result, keyLines, sourceVersion);
                } else {
                    parser.skipChildren();
                }
            }
            if (!foundItems)
                throw new IOException("Отсутствует поле: " + FIELD_ITEMS);
            if (parser.nextToken() != null)
                throw new IOException("Лишние данные после корневого объекта");
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
            if (token == null)
                throw new IOException("Неожиданный конец файла в массиве items");
            int line = lineOf(parser);
            JsonNode node = mapper.readTree(parser);
            try {
                CatalogItem item = parseItem(node, sourceVersion);
                Integer prev = keyLines.putIfAbsent(item.getItemNumber(), line);
                if (prev != null)
                    throw new MigrationException(MigrationException.Reason.CORRUPTED,
                            "повтор артикула " + item.getItemNumber()
                                    + " (первое вхождение в строке " + prev + ")");
                if (result.size() >= CatalogCodec.MAX_ITEMS)
                    throw new IOException("Превышен лимит " + CatalogCodec.MAX_ITEMS + " записей");
                result.add(item);
            } catch (MigrationException ex) {
                throw new IOException("Строка " + line + ": " + ex.getMessage(), ex);
            } catch (IOException ex) {
                throw new IOException("Строка " + line + ": " + ex.getMessage(), ex);
            }
        }
    }

    private CatalogItem parseItem(JsonNode node, int sourceVersion) throws IOException {
        if (!node.isObject())
            throw new MigrationException(MigrationException.Reason.CORRUPTED,
                    "Запись должна быть объектом JSON");

        String type = node.has("type") ? string(node, "type") : "Product";
        validateItemFields(node, type, sourceVersion);

        long id = integer(node, "itemNumber");
        String name = string(node, "productName");
        String category = string(node, "category");
        long price = integer(node, "price");
        int stock = integer(node, "remainder");

        String start = null;
        Integer months = null;
        if ("Warranty".equals(type)) {
            start = string(node, "startOfWarranty");
            months = integer(node, "warrantyMonths");
        }

        // --- staged migration: sourceVersion -> v3 ---
        String supplier = "";
        String countryOfOrigin = "";
        int discountPercent = 0;
        LocalDate manufactureDate = Product.DEFAULT_MANUFACTURE_DATE;

        if (sourceVersion >= CatalogVersions.V2) {
            supplier = optionalString(node, "supplier", "");
            countryOfOrigin = optionalString(node, "countryOfOrigin", "");
        }

        if (sourceVersion >= CatalogVersions.V3) {
            discountPercent = optionalInt(node, "discountPercent", 0);
            String mfg = optionalString(node, "manufactureDate", null);

            if (mfg != null && !mfg.isEmpty()) {
                try {
                    manufactureDate = LocalDate.parse(mfg);
                } catch (DateTimeParseException e) {
                    throw new MigrationException(MigrationException.Reason.BAD_FIELD,
                            "Некорректная дата manufactureDate: '" + mfg + "'...", e);
                }
            }
        }

        try {
            return CatalogCodec.create(type, id, name, category, price, stock, start, months,
                    supplier, countryOfOrigin, discountPercent, manufactureDate);
        } catch (IOException e) {
            throw new MigrationException(MigrationException.Reason.BAD_FIELD,
                    "Ошибка миграции v" + sourceVersion + " → v" + CatalogVersions.CURRENT
                            + " (артикул " + id + "): " + e.getMessage(), e);
        }
    }

    private static void validateItemFields(JsonNode node, String type, int sourceVersion)
            throws IOException {
        Set<String> allowed = new HashSet<>(BASE_ITEM_FIELDS);
        if ("Warranty".equals(type)) allowed.addAll(WARRANTY_EXTRA);
        if (sourceVersion >= 2 && !"Discontinued".equals(type)) allowed.addAll(V2_EXTRA);
        if (sourceVersion >= 3 && !"Discontinued".equals(type)) allowed.addAll(V3_EXTRA);

        Iterator<String> it = node.fieldNames();
        while (it.hasNext()) {
            String name = it.next();
            if (!allowed.contains(name))
                throw new IOException("Неизвестное поле в записи: \"" + name
                        + "\" (схема v" + sourceVersion + ")");
        }
    }

    private static JsonNode required(JsonNode node, String key) throws IOException {
        JsonNode v = node.get(key);
        if (v == null || v.isNull())
            throw new IOException("Отсутствует поле: " + key);
        return v;
    }
    private static String string(JsonNode node, String key) throws IOException {
        JsonNode v = required(node, key);
        if (!v.isTextual())
            throw new IOException("Ожидается строка: " + key);
        return v.textValue();
    }
    private static String optionalString(JsonNode node, String key, String def) {
        JsonNode v = node.get(key);
        if (v == null || v.isNull()) return def;
        return v.isTextual() ? v.textValue() : def;
    }
    private static int integer(JsonNode node, String key) throws IOException {
        JsonNode v = required(node, key);
        if (!v.isIntegralNumber() || !v.canConvertToInt())
            throw new IOException("Ожидается int: " + key);
        return v.intValue();
    }
    private static int optionalInt(JsonNode node, String key, int def) throws IOException {
        JsonNode v = node.get(key);
        if (v == null || v.isNull()) return def;
        if (!v.isIntegralNumber() || !v.canConvertToInt())
            throw new MigrationException(MigrationException.Reason.BAD_FIELD,
                    "Поле " + key + " должно быть int");
        return v.intValue();
    }
    private static int lineOf(JsonParser parser) {
        JsonLocation loc = parser.currentLocation();
        return loc == null ? -1 : loc.getLineNr();
    }
    private static IOException jsonError(JsonProcessingException ex) {
        JsonLocation loc = ex.getLocation();
        return new IOException(loc != null
                ? "Ошибка разбора JSON (строка " + loc.getLineNr()
                + ", колонка " + loc.getColumnNr() + "): " + ex.getOriginalMessage()
                : "Ошибка разбора JSON: " + ex.getOriginalMessage(), ex);
    }
}