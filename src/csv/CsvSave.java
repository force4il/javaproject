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

import model.CatalogItem;
import model.EntityType;
import model.Product;
import model.ProductWithWarranty;

public final class CsvSave {

    private static final char DELIMITER = ';';

    public static void save(List<CatalogItem> items, Path file) throws CsvException {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(file, "file");

        try (var writer = Files.newBufferedWriter(file);
             var csvWriter = new CSVWriterBuilder(writer)
                     .withSeparator(DELIMITER)
                     .build()) {

            for (CatalogItem item : items) {
                csvWriter.writeNext(toLine(item));
            }
        } catch (IOException e) {
            throw new CsvIOException(ErrorCode.FILE_WRITE,
                    "Не удалось сохранить файл: " + file, e);
        }
    }

    private static String[] toLine(CatalogItem item) {
        if (item instanceof ProductWithWarranty pw) {
            return new String[]{
                    EntityType.WARRANTY.getCsvName(),
                    String.valueOf(pw.getItemNumber()),
                    pw.getProductName(),
                    pw.getCategory(),
                    String.valueOf(pw.getPrice()),
                    String.valueOf(pw.getRemainder()),
                    pw.getStartOfWarranty() != null ? pw.getStartOfWarranty().toString() : "",
                    String.valueOf(pw.getWarrantyMonths())
            };
        }
        if (item instanceof Product p) {
            return new String[]{
                    EntityType.PRODUCT.getCsvName(),
                    String.valueOf(p.getItemNumber()),
                    p.getProductName(),
                    p.getCategory(),
                    String.valueOf(p.getPrice()),
                    String.valueOf(p.getRemainder())
            };
        }
        // DiscontinuedProduct (read-only)
        return new String[]{
                EntityType.DISCONTINUED.getCsvName(),
                String.valueOf(item.getItemNumber()),
                item.getProductName(),
                item.getCategory(),
                String.valueOf(item.getPrice()),
                String.valueOf(item.getRemainder())
        };
    }
}