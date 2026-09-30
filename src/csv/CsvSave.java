package csv;

import com.opencsv.CSVWriterBuilder;
import exceptions.CsvException;
import exceptions.CsvIOException;
import exceptions.ErrorCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import model.Product;
import model.ProductWithWarranty;
import model.DiscontinuedProduct;

public final class CsvSave {

    private static final char DELIMITER = ';';

    public static void save(List<Product> products,
                            List<DiscontinuedProduct> discontinuedProducts,
                            Path file) throws CsvException {

        Objects.requireNonNull(products, "products");
        Objects.requireNonNull(discontinuedProducts, "discontinuedProducts");
        Objects.requireNonNull(file, "file");

        try (var writer = Files.newBufferedWriter(file);
             var csvWriter = new CSVWriterBuilder(writer)
                     .withSeparator(DELIMITER)
                     .build()) {

            for (Product p : products) {
                csvWriter.writeNext(toLine(p));
            }

            for (DiscontinuedProduct d : discontinuedProducts) {
                csvWriter.writeNext(toLine(d));
            }
        } catch (IOException e) {
            throw new CsvIOException(ErrorCode.FILE_WRITE,
                    "Не удалось сохранить файл: " + file, e);
        }
    }

    //перевод объекта Product(и наследников) в строку для csv
    private static String[] toLine(Product p) {
        if (p instanceof ProductWithWarranty pw) {
            return new String[]{
                    "Warranty",
                    String.valueOf(pw.getItemNumber()),
                    pw.getProductName(),
                    pw.getCategory(),
                    String.valueOf(pw.getPrice()),
                    String.valueOf(pw.getRemainder()),
                    pw.getStartOfWarranty() != null ? pw.getStartOfWarranty().toString() : "",
                    String.valueOf(pw.getWarrantyMonths())
            };
        }

        return new String[]{
                "model.Product",
                String.valueOf(p.getItemNumber()),
                p.getProductName(),
                p.getCategory(),
                String.valueOf(p.getPrice()),
                String.valueOf(p.getRemainder())
        };
    }

    //перевод объекта DiscontinuedProduct в строку для csv
    private static String[] toLine(DiscontinuedProduct d) {
        return new String[]{
                "Discontinued",
                String.valueOf(d.itemNumber()),
                d.productName(),
                d.category(),
                String.valueOf(d.price()),
                String.valueOf(d.remainder())
        };
    }
}