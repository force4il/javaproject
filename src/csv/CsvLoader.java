package csv;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReaderBuilder;
import exceptions.CsvException;
import exceptions.CsvIOException;
import exceptions.CsvParseException;
import exceptions.ErrorCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


import model.CatalogItem;
import model.DiscontinuedProduct;
import model.EntityType;
import model.Product;
import model.ProductWithWarranty;
import model.Editable;

import java.math.BigInteger;

public final class CsvLoader {

    private static final char DELIMITER = ';';
    private static final int  FIELDS_PRODUCT = 6;
    private static final int  FIELDS_WARRANTY = 8;
    private static final int  FIELDS_DISCONTINUED = 6;

    public record SkippedRow(long lineNumber, CsvParseException cause) {}
    public record LoadResult(List<CatalogItem> items, List<SkippedRow> skippedRows) {}

    public LoadResult load(Path file) throws CsvException {
        Objects.requireNonNull(file, "file");

        List<CatalogItem> items   = new ArrayList<>();
        List<SkippedRow>  skipped = new ArrayList<>();

        CSVParser parser = new CSVParserBuilder().withSeparator(DELIMITER).build();

        try (var reader = Files.newBufferedReader(file);
             var csvReader = new CSVReaderBuilder(reader).withCSVParser(parser).build()) {

            String[] parts;
            while ((parts = csvReader.readNext()) != null) {
                long line = csvReader.getLinesRead();
                try {
                    addItem(parts, items);
                } catch (CsvParseException e) {
                    skipped.add(new SkippedRow(line, e));
                }
            }
        } catch (IOException e) {
            throw new CsvIOException(ErrorCode.FILE_READ,
                    "Ошибка чтения файла: " + file, e);
        } catch (com.opencsv.exceptions.CsvException e) {
            throw new CsvParseException(ErrorCode.PARSE_ERROR,
                    "Ошибка парсинга CSV: " + e.getMessage(), e.getCause());
        }

        return new LoadResult(items, skipped);
    }

    private static void addItem(String[] parts, List<CatalogItem> items)
            throws CsvParseException {

        if (parts.length == 0)
            return;

        String rawType = parts[0] == null ? "" : parts[0].trim();
        if (rawType.isEmpty())
            return;

        EntityType type = EntityType.fromCsvName(rawType)
                .orElseThrow(() -> new CsvParseException(
                        ErrorCode.UNKNOWN_TYPE,
                        "неизвестный тип записи: " + rawType));

        CatalogItem item = switch (type) {
            case PRODUCT -> parseProduct(parts);
            case WARRANTY -> parseWarranty(parts);
            case DISCONTINUED -> parseDiscontinued(parts);
        };

        validateItem(item);
        items.add(item);
    }

    private static void validateItem(CatalogItem item) throws CsvParseException {
        List<String> errors;
        if (item instanceof Editable editable) {
            errors = editable.validate();
        } else if (item instanceof DiscontinuedProduct dp) {
            errors = new ArrayList<>();
            if (dp.price()    < Product.MIN_NUM_BORDER)
                errors.add("price не может быть меньше " + Product.MIN_NUM_BORDER);
            if (dp.remainder() < Product.MIN_NUM_BORDER
                    || dp.remainder() >= Product.MAX_REMAINDER)
                errors.add("remainder вне диапазона ["
                        + Product.MIN_NUM_BORDER + ".." + Product.MAX_REMAINDER + ")");
        } else {
            return;
        }
        if (errors.isEmpty())
            return;

        throw new CsvParseException(ErrorCode.PARSE_ERROR,
                "запись с артикулом " + safeItemNumber(item)
                        + ": " + String.join("; ", errors));
    }

    private static String safeItemNumber(CatalogItem item) {
        try {
            return String.valueOf(item.getItemNumber());
        } catch (ArithmeticException e) {
            return "<вне int>";
        }
    }

    private static Product parseProduct(String[] p) throws CsvParseException {
        expect(p, FIELDS_PRODUCT, EntityType.PRODUCT.getCsvName());
        return new Product(parseItemNumber(p[1]),
                requireText(p[2], "productName"),
                requireText(p[3], "category"),
                parseInt(p[4], "price"),
                parseInt(p[5], "remainder"));
    }

    private static ProductWithWarranty parseWarranty(String[] p) throws CsvParseException {
        expect(p, FIELDS_WARRANTY, EntityType.WARRANTY.getCsvName());
        return new ProductWithWarranty(parseItemNumber(p[1]),   // ←
                requireText(p[2], "productName"),
                requireText(p[3], "category"),
                parseInt(p[4], "price"),
                parseInt(p[5], "remainder"),
                parseDate(p[6], "startOfWarranty"),
                parseInt(p[7], "warrantyMonths"));
    }

    private static DiscontinuedProduct parseDiscontinued(String[] p) throws CsvParseException {
        expect(p, FIELDS_DISCONTINUED, EntityType.DISCONTINUED.getCsvName());
        return new DiscontinuedProduct(parseItemNumber(p[1]),
                requireText(p[2], "productName"),
                requireText(p[3], "category"),
                parseInt(p[4], "price"),
                parseInt(p[5], "remainder"));
    }

    private static void expect(String[] parts, int expected, String type)
            throws CsvParseException {
        if (parts.length != expected)
            throw new CsvParseException(ErrorCode.BAD_FIELD_COUNT,
                    "для типа " + type + " ожидалось " + expected +
                            " полей, получено " + parts.length);
    }

    private static void checkEmptyOrNull(String value, String field)
            throws CsvParseException {
        if (value == null || value.isBlank())
            throw new CsvParseException(ErrorCode.BAD_FIELD,
                    "обязательное поле '" + field + "' не заполнено");
    }

    private static String requireText(String value, String field)
            throws CsvParseException {
        checkEmptyOrNull(value, field);
        return value.trim();
    }

    /** Парсит любое целое, включая выходящее за пределы int и long */
    private static BigInteger parseBigInteger(String value, String field)
            throws CsvParseException {
        checkEmptyOrNull(value, field);
        try {
            return new BigInteger(value.trim());
        } catch (NumberFormatException e) {
            throw new CsvParseException(ErrorCode.BAD_NUMBER,
                    "поле '" + field + "' не число: \"" + value + "\"", e);
        }
    }

    /** Целое число, влезает в int */
    private static int parseInt(String value, String field) throws CsvParseException {
        BigInteger bi = parseBigInteger(value, field);
        return toIntInRange(bi, value, field);
    }

    /** Для артикула: влезает в int и >= MIN_ITEM_NUMBER */
    private static int parseItemNumber(String value) throws CsvParseException {
        int v = parseInt(value, "itemNumber");
        if (v < Product.MIN_ITEM_NUMBER)
            throw new CsvParseException(ErrorCode.BAD_FIELD,
                    "поле 'itemNumber' должно быть >= " + Product.MIN_ITEM_NUMBER + ": " + v);
        return v;
    }

    /** Проверяет, что BigInteger попадает в диапазон int, и возвращает его как int */
    private static int toIntInRange(BigInteger bi, String raw, String field)
            throws CsvParseException {
        if (bi.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0
                || bi.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0)
            throw new CsvParseException(ErrorCode.BAD_INT_RANGE,
                    "поле '" + field + "' = " + raw.trim() + " не помещается в int ("
                            + Integer.MIN_VALUE + ".." + Integer.MAX_VALUE + ")");
        return bi.intValue();
    }

    private static LocalDate parseDate(String value, String field)
            throws CsvParseException {
        checkEmptyOrNull(value, field);
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new CsvParseException(ErrorCode.BAD_DATE,
                    "поле '" + field + "' не дата: \"" + value + "\"", e);
        }
    }
}