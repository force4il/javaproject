package gui;

import exceptions.TreeOperationException;
import model.CatalogItem;
import model.DiscontinuedProduct;
import model.Product;
import model.ProductWithWarranty;
import tree.BTree;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;

public final class BenchmarkRunner {

    public record Row(String operation, int n, long nanos) {
        public double millis() { return nanos / 1_000_000.0; }
    }

    /** Запускает полный замер на n элементов. */
    public static List<Row> run(int n, long seed, BooleanSupplier cancelled) {
        List<Row> rows = new ArrayList<>();
        int[] keys = randomKeys(n, seed);
        List<CatalogItem> items = makeItems(keys, seed);

        BTree<CatalogItem> tree = new BTree<>();

        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            if ((i & 0x3FF) == 0 && cancelled.getAsBoolean()) return rows;
            try { tree.insert(keys[i], items.get(i)); }
            catch (TreeOperationException ignore) { /* дубликатов нет */ }
        }
        rows.add(new Row("Вставка", n, System.nanoTime() - t0));

        t0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            if ((i & 0x3FF) == 0 && cancelled.getAsBoolean()) return rows;
            tree.search(keys[i]);
        }
        rows.add(new Row("Поиск", n, System.nanoTime() - t0));

        t0 = System.nanoTime();
        try {
            for (int r = 0; r < 100; r++) tree.rangeScan(keys[0], keys[n - 1]);
        } catch (TreeOperationException ignore) {}
        rows.add(new Row("Диапазонный скан ×100", n, System.nanoTime() - t0));

        t0 = System.nanoTime();
        BTree.FillStats st = tree.stats();
        rows.add(new Row("Статистика", st.nodes(), System.nanoTime() - t0));

        t0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            if ((i & 0x3FF) == 0 && cancelled.getAsBoolean()) return rows;
            try { tree.delete(keys[i]); }
            catch (TreeOperationException ignore) {}
        }
        rows.add(new Row("Удаление", n, System.nanoTime() - t0));

        return rows;
    }

    static int[] randomKeys(int n, long seed) {
        Random rnd = new Random(seed);
        int[] keys = new int[n];
        java.util.HashSet<Integer> used = new java.util.HashSet<>(n * 2);
        for (int i = 0; i < n; i++) {
            int k;
            do { k = rnd.nextInt(Integer.MAX_VALUE / 2) + 1; } while (!used.add(k));
            keys[i] = k;
        }
        return keys;
    }

    static List<CatalogItem> makeItems(int[] keys, long seed) {
        Random rnd = new Random(seed ^ 0x9E3779B9L);
        List<CatalogItem> out = new ArrayList<>(keys.length);
        for (int k : keys) {
            int kind = rnd.nextInt(3);
            String name = "Item" + k;
            String cat  = "Cat" + (rnd.nextInt(5) + 1);
            int price   = 100 + rnd.nextInt(900);
            int rem     = rnd.nextInt(500);
            switch (kind) {
                case 0 -> out.add(new Product(k, name, cat, price, rem));
                case 1 -> out.add(new ProductWithWarranty(k, name, cat, price, rem,
                        LocalDate.now().minusDays(rnd.nextInt(365)),
                        6 + rnd.nextInt(24)));
                default -> out.add(new DiscontinuedProduct(k, name, cat, price, rem));
            }
        }
        return out;
    }
}