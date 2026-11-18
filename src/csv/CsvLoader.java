package csv;

import com.opencsv.*;
import exceptions.*;
import model.*;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

public final class CsvLoader {
    private static final char DELIMITER = ';';

    public record SkippedRow(long lineNumber, CsvParseException cause) {}
    public record LoadResult(List<CatalogItem> items, List<SkippedRow> skippedRows, int sourceVersion) {}

    public LoadResult load(Path file) throws CsvException {
        Objects.requireNonNull(file, "file");
        List<CatalogItem> items = new ArrayList<>();
        List<SkippedRow> skipped = new ArrayList<>();
        int sourceVersion = CsvSchema.MIN_VERSION;

        CSVParser parser = new CSVParserBuilder().withSeparator(DELIMITER).build();
        try (var reader = Files.newBufferedReader(file);
             var csvReader = new CSVReaderBuilder(reader).withCSVParser(parser).build()) {

            String[] parts;
            boolean headerSeen = false;
            while ((parts = csvReader.readNext()) != null) {
                long line = csvReader.getLinesRead();
                if (!headerSeen) {
                    headerSeen = true;
                    if (parts.length == 1 && parts[0] != null) {
                        String h = parts[0];
                        if (h.startsWith("\uFEFF")) h = h.substring(1);
                        if (h.startsWith("#schema_version=")) {
                            try {
                                sourceVersion = Integer.parseInt(h.substring("#schema_version=".length()).trim());
                            } catch (NumberFormatException e) {
                                throw new CsvParseException(ErrorCode.PARSE_ERROR,
                                        "Некорректный заголовок схемы: \"" + parts[0] + "\"", e);
                            }
                            if (sourceVersion < CsvSchema.MIN_VERSION || sourceVersion > CsvSchema.CURRENT_VERSION)
                                throw new CsvParseException(ErrorCode.PARSE_ERROR,
                                        "Неподдерживаемая версия CSV: " + sourceVersion);
                            continue;
                        }
                    }
                }
                try {
                    addItem(parts, items, sourceVersion);
                } catch (CsvParseException e) {
                    skipped.add(new SkippedRow(line, e));
                }
            }
        } catch (IOException e) {
            throw new CsvIOException(ErrorCode.FILE_READ, "Ошибка чтения файла: " + file, e);
        } catch (com.opencsv.exceptions.CsvException e) {
            throw new CsvParseException(ErrorCode.PARSE_ERROR,
                    "Ошибка парсинга CSV: " + e.getMessage(), e.getCause());
        }
        return new LoadResult(items, skipped, sourceVersion);
    }

    private static void addItem(String[] parts, List<CatalogItem> items, int version)
            throws CsvParseException {
        if (parts.length == 0) return;

        // Переменная теперь effectively final, так как инициализируется один раз
        String rawType = cleanTypeName(parts[0]);

        if (rawType.isEmpty()) return;

        EntityType type = EntityType.fromCsvName(rawType)
                .orElseThrow(() -> new CsvParseException(ErrorCode.UNKNOWN_TYPE,
                        "неизвестный тип записи: " + rawType));

        CatalogItem item = switch (type) {
            case PRODUCT -> parseProduct(parts, version);
            case WARRANTY -> parseWarranty(parts, version);
            case DISCONTINUED -> parseDiscontinued(parts, version);
        };
        validateItem(item);
        items.add(item);
    }

    /** Удаляет BOM и лишние пробелы, возвращает пустую строку, если parts[0] == null */
    private static String cleanTypeName(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (!trimmed.isEmpty() && trimmed.charAt(0) == '\uFEFF') {
            trimmed = trimmed.substring(1).trim();
        }
        return trimmed;
    }
    // ---- staged migration for CSV: parse v1/v2 fields, fill v3 defaults ----

    private static Product parseProduct(String[] p, int version) throws CsvParseException {
        expect(p, CsvSchema.expectedColumns(EntityType.PRODUCT, version),
                "Product v" + version);
        long id = parseItemNumber(p[1]);
        String name = requireText(p[2], "productName");
        String category = requireText(p[3], "category");
        int price = parseInt(p[4], "price");
        int remainder = parseInt(p[5], "remainder");

        String supplier = "";
        String countryOfOrigin = "";
        int discountPercent = 0;
        LocalDate manufactureDate = null;

        int i = 6;
        if (version >= 2) {
            supplier = optionalText(p[i++]);
            countryOfOrigin = optionalText(p[i++]);
        }
        if (version >= 3) {
            discountPercent = parseInt(p[i++], "discountPercent");
            manufactureDate = parseDateOrDefault(p[i++], "manufactureDate");
        }

        return new Product(id, name, category, price, remainder,
                supplier, countryOfOrigin, discountPercent, manufactureDate);
    }

    private static ProductWithWarranty parseWarranty(String[] p, int version) throws CsvParseException {
        expect(p, CsvSchema.expectedColumns(EntityType.WARRANTY, version),
                "Warranty v" + version);
        long id = parseItemNumber(p[1]);
        String name = requireText(p[2], "productName");
        String category = requireText(p[3], "category");
        int price = parseInt(p[4], "price");
        int remainder = parseInt(p[5], "remainder");
        LocalDate start = parseDate(p[6], "startOfWarranty");
        int months = parseInt(p[7], "warrantyMonths");

        String supplier = "";
        String countryOfOrigin = "";
        int discountPercent = 0;
        LocalDate manufactureDate = null;

        int i = 8;
        if (version >= 2) {
            supplier = optionalText(p[i++]);
            countryOfOrigin = optionalText(p[i++]);
        }
        if (version >= 3) {
            discountPercent = parseInt(p[i++], "discountPercent");
            manufactureDate = parseDateOrDefault(p[i++], "manufactureDate");
        }

        return new ProductWithWarranty(id, name, category, price, remainder,
                start, months, supplier, countryOfOrigin, discountPercent, manufactureDate);
    }

    private static LocalDate parseDateOrDefault(String v, String field) throws CsvParseException {
        if (v == null || v.isBlank()) return Product.DEFAULT_MANUFACTURE_DATE;
        try {
            return LocalDate.parse(v.trim());
        } catch (DateTimeParseException e) {
            throw new CsvParseException(ErrorCode.BAD_DATE,
                    "поле '" + field + "' не дата: \"" + v + "\"", e);
        }
    }

    private static DiscontinuedProduct parseDiscontinued(String[] p, int version) throws CsvParseException {
        expect(p, CsvSchema.expectedColumns(EntityType.DISCONTINUED, version),
                "Discontinued v" + version);
        return new DiscontinuedProduct(parseItemNumber(p[1]),
                requireText(p[2], "productName"),
                requireText(p[3], "category"),
                parseInt(p[4], "price"),
                parseInt(p[5], "remainder"));
    }

    private static void validateItem(CatalogItem item) throws CsvParseException {
        List<String> errors = new ArrayList<>();
        if (item instanceof Editable editable)
            errors.addAll(editable.validate());
        else if (item instanceof DiscontinuedProduct dp) {
            if (dp.price() < Product.MIN_NUM_BORDER)
                errors.add("price не может быть меньше " + Product.MIN_NUM_BORDER);
            if (dp.remainder() < Product.MIN_NUM_BORDER || dp.remainder() >= Product.MAX_REMAINDER)
                errors.add("remainder вне диапазона [" + Product.MIN_NUM_BORDER
                        + ".." + Product.MAX_REMAINDER + ")");
        }
        if (errors.isEmpty()) return;
        throw new CsvParseException(ErrorCode.PARSE_ERROR,
                "запись с артикулом " + safeItemNumber(item) + ": "
                        + String.join("; ", errors));
    }

    private static String safeItemNumber(CatalogItem item) {
        try { return String.valueOf(item.getItemNumber()); }
        catch (ArithmeticException e) { return "<вне int>"; }
    }

    // ---------- primitive parsers (без изменений) ----------

    private static void expect(String[] p, int expected, String tag) throws CsvParseException {
        if (p.length != expected)
            throw new CsvParseException(ErrorCode.BAD_FIELD_COUNT,
                    "для " + tag + " ожидалось " + expected + " полей, получено " + p.length);
    }

    private static String optionalText(String v) { return v == null ? "" : v; }

    private static void checkEmptyOrNull(String v, String field) throws CsvParseException {
        if (v == null || v.isBlank())
            throw new CsvParseException(ErrorCode.BAD_FIELD,
                    "обязательное поле '" + field + "' не заполнено");
    }

    private static String requireText(String v, String field) throws CsvParseException {
        checkEmptyOrNull(v, field); return v.trim();
    }

    private static BigInteger parseBigInteger(String v, String field) throws CsvParseException {
        checkEmptyOrNull(v, field);
        try { return new BigInteger(v.trim()); }
        catch (NumberFormatException e) {
            throw new CsvParseException(ErrorCode.BAD_NUMBER,
                    "поле '" + field + "' не число: \"" + v + "\"", e);
        }
    }

    private static int parseInt(String v, String field) throws CsvParseException {
        BigInteger bi = parseBigInteger(v, field);
        if (bi.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0
                || bi.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0)
            throw new CsvParseException(ErrorCode.BAD_INT_RANGE,
                    "поле '" + field + "' = " + v.trim() + " не помещается в int");
        return bi.intValue();
    }

    private static int parseItemNumber(String v) throws CsvParseException {
        int n = parseInt(v, "itemNumber");
        if (n < Product.MIN_ITEM_NUMBER)
            throw new CsvParseException(ErrorCode.BAD_FIELD,
                    "поле 'itemNumber' должно быть >= " + Product.MIN_ITEM_NUMBER + ": " + n);
        return n;
    }

    private static LocalDate parseDate(String v, String field) throws CsvParseException {
        checkEmptyOrNull(v, field);
        try { return LocalDate.parse(v.trim()); }
        catch (DateTimeParseException e) {
            throw new CsvParseException(ErrorCode.BAD_DATE,
                    "поле '" + field + "' не дата: \"" + v + "\"", e);
        }
    }
}