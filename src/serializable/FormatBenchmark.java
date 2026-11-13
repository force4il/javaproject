package serializable;

import model.CatalogItem;
import model.ProductWithWarranty;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;

public final class FormatBenchmark {
    private FormatBenchmark() {}

    public record Result(String format, long bytes, double saveMillis, double loadMillis) {}

    public static Result measure(Serializer<List<CatalogItem>> serializer, List<CatalogItem> items, Path folder) throws IOException {
        Files.createDirectories(folder);
        Path file = Files.createTempFile(folder, "catalog-bench-", "." + serializer.format());
        try {
            long save = 0, load = 0;
            for (int i = 0; i < 5; i++) {
                CatalogCodec.checkCancelled();

                long t = System.nanoTime();
                serializer.save(items, file);
                save += System.nanoTime() - t;

                CatalogCodec.checkCancelled();

                t = System.nanoTime();
                List<CatalogItem> restored = serializer.load(file).data();
                load += System.nanoTime() - t;

                CatalogCodec.checkCancelled();

                if (restored.size() != items.size())
                    throw new IOException("Изменилось количество записей");

                for (int j = 0; j < items.size(); j++)
                    if (!isSameData(items.get(j), restored.get(j)))
                        throw new IOException("Данные были повреждены при сериализации/десериализации. "
                                + "Исходный: " + items.get(j) + ", Восстановленный: " + restored.get(j));
            }
            return new Result(serializer.format(), Files.size(file), save / 5_000_000.0, load / 5_000_000.0);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static boolean isSameData(CatalogItem a, CatalogItem b) {
        if (a == null && b == null)
            return true;

        if (a == null || b == null)
            return false;

        // Сравниваем базовые поля
        if (a.getType() != b.getType())
            return false;
        if (a.getItemNumber() != b.getItemNumber())
            return false;
        if (!a.getProductName().equals(b.getProductName()))
            return false;
        if (!a.getCategory().equals(b.getCategory()))
            return false;
        if (a.getPrice() != b.getPrice())
            return false;
        if (a.getRemainder() != b.getRemainder())
            return false;

        if (a instanceof ProductWithWarranty pwA && b instanceof ProductWithWarranty pwB) {
            if (!pwA.getStartOfWarranty().equals(pwB.getStartOfWarranty()))
                return false;
            return pwA.getWarrantyMonths() == pwB.getWarrantyMonths();
        }

        return true;
    }
}