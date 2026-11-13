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
            // 1. Создаем временный файл в ТОЙ ЖЕ директории, что и целевой.
            // Это критически важно: ATOMIC_MOVE работает только в пределах одной файловой системы.
            temp = Files.createTempFile(parent, "catalog-csv-", ".tmp");

            // 2. Пишем данные во временный файл
            try (var writer = Files.newBufferedWriter(temp);
                 var csvWriter = new CSVWriterBuilder(writer).withSeparator(DELIMITER).build()) {
                for (CatalogItem item : items) {
                    csvWriter.writeNext(toLine(item));
                }
            }

            // 3. Атомарно подменяем целевой файл (если ОС поддерживает)
            Files.move(temp, target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

        } catch (IOException e) {
            throw new CsvIOException(ErrorCode.FILE_WRITE,
                    "Не удалось сохранить файл: " + file, e);
        } finally {
            // 4. В любом случае пытаемся удалить временный файл, если он остался
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // Если не удалось удалить временный файл, не засоряем консоль.
                    // Основная операция (сохранение) уже либо успешна, либо упала с ошибкой.
                }
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