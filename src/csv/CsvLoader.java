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

public final class CsvLoader {

    private static final char DELIMITER = ';';
    private static final int FIELDS_PRODUCT      = 6;
    private static final int FIELDS_WARRANTY     = 8;
    private static final int FIELDS_DISCONTINUED = 6;

    /** Класс для работы с битыми строками */
    public record SkippedRow(long lineNumber, CsvParseException cause) {}

    public record LoadResult(List<CatalogItem> items, List<SkippedRow> skippedRows) {}

    public LoadResult load(Path file) throws CsvException {
        Objects.requireNonNull(file, "file");

        List<CatalogItem> items = new ArrayList<>();
        List<SkippedRow> skipped = new ArrayList<>();

        CSVParser parser = new CSVParserBuilder()
                .withSeparator(DELIMITER)
                .build();

        try (var reader = Files.newBufferedReader(file);
             var csvReader = new CSVReaderBuilder(reader)
                     .withCSVParser(parser)
                     .build()) {

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
        if (parts.length == 0) return;

        String rawType = (parts[0] == null) ? "" : parts[0].trim();
        if (rawType.isEmpty()) return;

        EntityType type = EntityType.fromCsvName(rawType);
        if (type == null) {
            throw new CsvParseException(ErrorCode.UNKNOWN_TYPE,
                    "неизвестный тип записи: " + rawType);
        }

        switch (type) {
            case PRODUCT      -> items.add(parseProduct(parts));
            case WARRANTY     -> items.add(parseWarranty(parts));
            case DISCONTINUED -> items.add(parseDiscontinued(parts));
        }
    }

    // Product;itemNumber;productName;category;price;remainder
    private static Product parseProduct(String[] p) throws CsvParseException {
        expect(p, FIELDS_PRODUCT, EntityType.PRODUCT.getCsvName());
        return new Product(
                parseInt(p[1], "itemNumber"),
                requireText(p[2], "productName"),
                requireText(p[3], "category"),
                parseInt(p[4], "price"),
                parseInt(p[5], "remainder"));
    }

    // Warranty;itemNumber;productName;category;price;remainder;startOfWarranty;warrantyMonths
    private static ProductWithWarranty parseWarranty(String[] p) throws CsvParseException {
        expect(p, FIELDS_WARRANTY, EntityType.WARRANTY.getCsvName());
        return new ProductWithWarranty(
                parseInt(p[1], "itemNumber"),
                requireText(p[2], "productName"),
                requireText(p[3], "category"),
                parseInt(p[4], "price"),
                parseInt(p[5], "remainder"),
                parseDate(p[6], "startOfWarranty"),
                parseInt(p[7], "warrantyMonths"));
    }

    // Discontinued;itemNumber;productName;category;price;remainder
    private static DiscontinuedProduct parseDiscontinued(String[] p) throws CsvParseException {
        expect(p, FIELDS_DISCONTINUED, EntityType.DISCONTINUED.getCsvName());
        return new DiscontinuedProduct(
                parseInt(p[1], "itemNumber"),
                requireText(p[2], "productName"),
                requireText(p[3], "category"),
                parseInt(p[4], "price"),
                parseInt(p[5], "remainder"));
    }

    private static void expect(String[] parts, int expected, String type)
            throws CsvParseException {
        if (parts.length != expected) {
            throw new CsvParseException(ErrorCode.BAD_FIELD_COUNT,
                    "для типа " + type + " ожидалось " + expected +
                            " полей, получено " + parts.length);
        }
    }

    private static void checkEmptyOrNull(String value, String field)
            throws CsvParseException {
        if (value == null || value.isBlank()) {
            throw new CsvParseException(ErrorCode.BAD_FIELD,
                    "обязательное поле '" + field + "' не заполнено");
        }
    }

    private static String requireText(String value, String field)
            throws CsvParseException {
        checkEmptyOrNull(value, field);
        return value.trim();
    }

    private static int parseInt(String value, String field)
            throws CsvParseException {
        checkEmptyOrNull(value, field);
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new CsvParseException(ErrorCode.BAD_NUMBER,
                    "поле '" + field + "' не является числом: \"" + value + "\"", e);
        }
    }

    private static LocalDate parseDate(String value, String field)
            throws CsvParseException {
        checkEmptyOrNull(value, field);
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new CsvParseException(ErrorCode.BAD_DATE,
                    "поле '" + field + "' не является датой: \"" + value + "\"", e);
        }
    }
}