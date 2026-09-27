import exceptions.CsvException;
import exceptions.CsvIOException;
import exceptions.CsvParseException;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        Path file = Path.of("data.csv");

        saveDemo(file);
        System.out.println();

        loadDemo(file);
    }

    // ---------- выгрузка в CSV ----------

    private static void saveDemo(Path file) {
        List<Product> products = List.of(
                new Product(1, "Phone", "Electronics", 1000, 5),
                new ProductWithWarranty(3, "Laptop", "Electronics", 3000, 2,
                        LocalDate.of(2026, 1, 1), 24),
                new Product(7, "Chair", "Furniture", 250, 40)
        );
        List<DiscontinuedProduct> discontinued = List.of(
                new DiscontinuedProduct(42, "Old TV", "Electronics", 100, 0)
        );

        try {
            CsvSave.save(products, discontinued, file);
            System.out.println("Сохранено: " + file.toAbsolutePath());
        } catch (CsvParseException e) {
            System.err.println("Ошибка CSV: " + e.getMessage());
        } catch (CsvIOException e) {
            System.err.println("Ошибка файла: " + e.getMessage());
        } catch (CsvException e) {
            System.err.println("Ошибка: " + e.getMessage());
        }
    }

    // ---------- загрузка из CSV ----------

    private static void loadDemo(Path file) {
        try {
            CsvLoader.LoadResult result = new CsvLoader().load(file);

            System.out.println("Обычные товары:");
            for (Product p : result.products()) {
                System.out.println("  " + p);
                // ProductWithWarranty — тоже Product, поэтому лежит в этом же списке.
                // Если нужен именно гарантийный товар — instanceOf + каст:
                if (p instanceof ProductWithWarranty pw) {
                    System.out.println("    гарантия до: " + pw.getWarrantyEndDate()
                            + " (действует: " + pw.isUnderWarranty() + ")");
                }
            }

            System.out.println("Снятые с производства:");
            for (DiscontinuedProduct d : result.discontinuedProducts()) {
                System.out.println("  " + d);
            }

            // Битые строки — не потерялись, их видно и можно залогировать/показать.
            if (!result.skippedRows().isEmpty()) {
                System.out.println("Пропущенные строки:");
                for (CsvLoader.SkippedRow s : result.skippedRows()) {
                    System.out.println("  строка " + s.lineNumber()
                            + ": " + s.cause().getMessage());
                }
            }

        } catch (CsvParseException e) {
            System.err.println("Ошибка CSV: " + e.getMessage());
        } catch (CsvIOException e) {
            System.err.println("Ошибка файла: " + e.getMessage());
        } catch (CsvException e) {
            System.err.println("Ошибка: " + e.getMessage());
        }
    }
}