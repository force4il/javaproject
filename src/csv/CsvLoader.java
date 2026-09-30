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

import model.Product;
import model.ProductWithWarranty;
import model.DiscontinuedProduct;

public final class CsvLoader {

    private static final char DELIMITER = ';';
    private static final int FIELDS_PRODUCT      = 6;
    private static final int FIELDS_WARRANTY     = 8;
    private static final int FIELDS_DISCONTINUED = 6;

    /**Класс для работы с битыми строками*/
    public record SkippedRow(long lineNumber, CsvParseException cause) {}

    public record LoadResult(List<Product> products,
                             List<DiscontinuedProduct> discontinuedProducts,
                             List<SkippedRow> skippedRows) {}

    /**
     * Читает файл построчно. Битые строки не останавливают загрузку,
     * данные о них записываются.
     */
    public LoadResult load(Path file) throws CsvException {
        Objects.requireNonNull(file, "file");

        List<Product> products = new ArrayList<>();
        List<DiscontinuedProduct> discontinued = new ArrayList<>();
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
                    addItem(parts, products, discontinued);
                } catch (CsvParseException e) {
                    skipped.add(new SkippedRow(line, e)); //добавляем битую строку
                }
            }
        } catch (IOException e) {
            throw new CsvIOException(ErrorCode.FILE_READ,
                    "Ошибка чтения файла: " + file, e);
        } catch (com.opencsv.exceptions.CsvException e) {
            throw new CsvParseException(ErrorCode.PARSE_ERROR,
                    "Ошибка парсинга CSV: " + e.getMessage(), e.getCause());
        }

        return new LoadResult(products, discontinued, skipped);
    }

    //добавляет запись в список по соответствующему типу
    private static void addItem(String[] parts,
                                  List<Product> products,
                                  List<DiscontinuedProduct> discontinued)
            throws CsvParseException {
        if (parts.length == 0)
            return;

        String type = (parts[0] == null) ? "" : parts[0].trim();
        switch (type) {
            case "model.Product" -> products.add(parseProduct(parts));
            case "Warranty" -> products.add(parseWarranty(parts));
            case "Discontinued" -> discontinued.add(parseDiscontinued(parts));
            case "" -> { /* пустая строка — просто пропуск */ }
            default -> throw new CsvParseException(ErrorCode.UNKNOWN_TYPE,
                    "неизвестный тип записи: " + type);
        }
    }

    //парсеры
    //model.Product;itemNumber;productName;category;price;remainder
    private static Product parseProduct(String[] p) throws CsvParseException {
        expect(p, FIELDS_PRODUCT, "model.Product");
        return new Product(
                parseInt(p[1], "itemNumber"),
                requireText(p[2], "productName"),
                requireText(p[3], "category"),
                parseInt(p[4], "price"),
                parseInt(p[5], "remainder"));
    }

    //Warranty;itemNumber;productName;category;price;remainder;startOfWarranty;warrantyMonths
    private static ProductWithWarranty parseWarranty(String[] p) throws CsvParseException {
        expect(p, FIELDS_WARRANTY, "Warranty");
        return new ProductWithWarranty(
                parseInt(p[1], "itemNumber"),
                requireText(p[2], "productName"),
                requireText(p[3], "category"),
                parseInt(p[4], "price"),
                parseInt(p[5], "remainder"),
                parseDate(p[6], "startOfWarranty"),
                parseInt(p[7], "warrantyMonths"));
    }

    //Discontinued;itemNumber;productName;category;price;remainder
    private static DiscontinuedProduct parseDiscontinued(String[] p) throws CsvParseException {
        expect(p, FIELDS_DISCONTINUED, "Discontinued");
        return new DiscontinuedProduct(
                parseInt(p[1], "itemNumber"),
                requireText(p[2], "productName"),
                requireText(p[3], "category"),
                parseInt(p[4], "price"),
                parseInt(p[5], "remainder"));
    }

    //проверка верного кол-ва переданных полей
    private static void expect(String[] parts, int expected, String type)
            throws CsvParseException {
        if (parts.length != expected) {
            throw new CsvParseException(ErrorCode.BAD_FIELD_COUNT,
                    "для типа " + type + " ожидалось " + expected +
                            " полей, получено " + parts.length);
        }
    }

    //проверка обязательного поля на пустоту или null
    private static void CheckEmptyOrNull(String value, String field)
            throws CsvParseException {
        if (value == null || value.isBlank()) {
            throw new CsvParseException(ErrorCode.BAD_FIELD,
                    "обязательное поле '" + field + "' не заполнено");
        }
    }

    //проверка null строк
    private static String requireText(String value, String field)
            throws CsvParseException {
        CheckEmptyOrNull(value, field);
        return value.trim();
    }

    //перевод строки в число
    private static int parseInt(String value, String field)
            throws CsvParseException {
        CheckEmptyOrNull(value, field);
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new CsvParseException(ErrorCode.BAD_NUMBER,
                    "поле '" + field + "' не является числом: \"" + value + "\"", e);
        }
    }

    //перевод строки в дату
    private static LocalDate parseDate(String value, String field)
            throws CsvParseException {
        CheckEmptyOrNull(value, field);
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new CsvParseException(ErrorCode.BAD_DATE,
                    "поле '" + field + "' не является датой: \"" + value + "\"", e);
        }
    }
}