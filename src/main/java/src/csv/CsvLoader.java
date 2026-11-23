package src.csv;

import com.opencsv.*;
import src.exceptions.*;
import src.model.*;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

import src.serializable.CatalogMigrations;
import src.serializable.CatalogRecord;
import src.exceptions.MigrationException;

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
                        if (h.startsWith("\uFEFF"))
                            h = h.substring(1);

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
        if (parts.length == 0)
            return;

        String rawType = cleanTypeName(parts[0]);
        if (rawType.isEmpty())
            return;

        EntityType type = EntityType.fromCsvName(rawType)
                .orElseThrow(() -> new CsvParseException(ErrorCode.UNKNOWN_TYPE, "неизвестный тип записи: " + rawType));

        CatalogRecord r = parseRecord(parts, type, version);
        CatalogMigrations.migrateToCurrent(r, version);

        try {
            items.add(CatalogMigrations.toItem(r));
        } catch (MigrationException e) {
            throw new CsvParseException(ErrorCode.PARSE_ERROR, e.getMessage(), e);
        } catch (IOException e) {
            throw new CsvParseException(ErrorCode.PARSE_ERROR,
                    "запись с артикулом " + r.itemNumber + ": " + e.getMessage(), e);
        }
    }

    private static CatalogRecord parseRecord(String[] p, EntityType type, int version)
            throws CsvParseException {
        expect(p, CsvSchema.expectedColumns(type, version), type.getCsvName() + " v" + version);

        CatalogRecord r = new CatalogRecord();
        r.type = type.getCsvName();
        r.itemNumber = parseItemNumber(p[1]);
        r.productName = requireText(p[2], "productName");
        r.category = requireText(p[3], "category");
        r.price = parseInt(p[4], "price");
        r.remainder = parseInt(p[5], "remainder");

        int i = 6;
        if (type == EntityType.WARRANTY) {
            r.startOfWarranty = parseDate(p[i++], "startOfWarranty").toString();
            r.warrantyMonths = parseInt(p[i++], "warrantyMonths");
        }

        if (version >= 2 && type != EntityType.DISCONTINUED) {
            r.supplier = optionalText(p[i++]);
            r.countryOfOrigin = optionalText(p[i++]);
        }
        if (version >= 3 && type != EntityType.DISCONTINUED) {
            r.discountPercent = parseInt(p[i++], "discountPercent");
            String mfgRaw = optionalText(p[i++]);
            if (!mfgRaw.isBlank())
                r.manufactureDate = parseDate(mfgRaw, "manufactureDate").toString();
        }
        return r;
    }

    private static String cleanTypeName(String raw) {
        if (raw == null)
            return "";

        String trimmed = raw.trim();
        if (!trimmed.isEmpty() && trimmed.charAt(0) == '\uFEFF')
            trimmed = trimmed.substring(1).trim();

        return trimmed;
    }

    private static void expect(String[] p, int expected, String tag) throws CsvParseException {
        if (p.length != expected)
            throw new CsvParseException(ErrorCode.BAD_FIELD_COUNT,
                    "для " + tag + " ожидалось " + expected + " полей, получено " + p.length);
    }

    private static String optionalText(String v) {
        return v == null ? "" : v;
    }

    private static void checkEmptyOrNull(String v, String field) throws CsvParseException {
        if (v == null || v.isBlank())
            throw new CsvParseException(ErrorCode.BAD_FIELD, "обязательное поле '" + field + "' не заполнено");
    }

    private static String requireText(String v, String field) throws CsvParseException {
        checkEmptyOrNull(v, field);
        return v.trim();
    }

    private static BigInteger parseBigInteger(String v, String field) throws CsvParseException {
        checkEmptyOrNull(v, field);
        try {
            return new BigInteger(v.trim());
        } catch (NumberFormatException e) {
            throw new CsvParseException(ErrorCode.BAD_NUMBER, "поле '" + field + "' не число: \"" + v + "\"", e);
        }
    }

    private static int parseInt(String v, String field) throws CsvParseException {
        BigInteger bi = parseBigInteger(v, field);
        if (bi.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0 || bi.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0)
            throw new CsvParseException(ErrorCode.BAD_INT_RANGE, "поле '" + field + "' = " + v.trim() + " не помещается в int");

        return bi.intValue();
    }

    private static int parseItemNumber(String v) throws CsvParseException {
        int n = parseInt(v, "itemNumber");
        if (n < Product.MIN_ITEM_NUMBER)
            throw new CsvParseException(ErrorCode.BAD_FIELD, "поле 'itemNumber' должно быть >= " + Product.MIN_ITEM_NUMBER + ": " + n);

        return n;
    }

    private static LocalDate parseDate(String v, String field) throws CsvParseException {
        checkEmptyOrNull(v, field);
        try { return LocalDate.parse(v.trim()); }
        catch (DateTimeParseException e) {
            throw new CsvParseException(ErrorCode.BAD_DATE, "поле '" + field + "' не дата: \"" + v + "\"", e);
        }
    }
}