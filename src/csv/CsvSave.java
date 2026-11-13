package csv;

import com.opencsv.CSVWriterBuilder;
import exceptions.CsvException;
import exceptions.CsvIOException;
import exceptions.ErrorCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;

import model.CatalogItem;
import model.DiscontinuedProduct;
import model.EntityType;
import model.Product;
import model.ProductWithWarranty;

public final class CsvSave {

    private static final char DELIMITER = ';';

    public static void save(List<CatalogItem> items, Path file) throws CsvException {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(file, "file");

        Path target = file.toAbsolutePath();
        Path parent = target.getParent();
        Path temp = null;

        try {
            temp = Files.createTempFile(parent, "catalog-csv-", ".tmp");
            try (var writer = Files.newBufferedWriter(temp);
                 var csvWriter = new CSVWriterBuilder(writer).withSeparator(DELIMITER).build()) {
                for (CatalogItem item : items)
                    csvWriter.writeNext(toLine(item));
            }

            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new CsvIOException(ErrorCode.FILE_WRITE,
                    "Не удалось сохранить файл: " + file, e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {}
            }
        }
    }

    private static String[] toLine(CatalogItem item) {
        return switch (item) {
            case ProductWithWarranty pw -> new String[]{
                    EntityType.WARRANTY.getCsvName(),
                    String.valueOf(pw.getItemNumber()),
                    pw.getProductName(), pw.getCategory(),
                    String.valueOf(pw.getPrice()), String.valueOf(pw.getRemainder()),
                    pw.getStartOfWarranty() != null ? pw.getStartOfWarranty().toString() : "",
                    String.valueOf(pw.getWarrantyMonths())
            };
            case Product p -> new String[]{
                    EntityType.PRODUCT.getCsvName(),
                    String.valueOf(p.getItemNumber()),
                    p.getProductName(), p.getCategory(),
                    String.valueOf(p.getPrice()), String.valueOf(p.getRemainder())
            };
            case DiscontinuedProduct dp -> new String[]{
                    EntityType.DISCONTINUED.getCsvName(),
                    String.valueOf(dp.getItemNumber()),
                    dp.getProductName(), dp.getCategory(),
                    String.valueOf(dp.getPrice()), String.valueOf(dp.getRemainder())
            };
            default -> throw new IllegalStateException(
                    "Неподдерживаемый тип CatalogItem: " + item.getClass().getName());
        };
    }
}