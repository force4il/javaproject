package gui;

import exceptions.TreeOperationException;
import model.CatalogItem;
import service.DataGenerator;
import tree.BTree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;

public final class BenchmarkRunner {

    private static final int STEP_MASK = 0x3FF;

    private static final int RANGE_SCAN_REPEATS = 100;

    private static final int KEY_BOUND = Integer.MAX_VALUE / 2;

    private BenchmarkRunner() {}

    public record Row(String operation, int n, long nanos) {
        public double millis() { return nanos / 1_000_000.0; }
    }

    public static List<Row> run(int n, long seed, BooleanSupplier cancelled) {
        List<Row> rows = new ArrayList<>();
        int[] keys = randomKeys(n, seed);
        List<CatalogItem> items = makeItems(keys, seed);

        BTree<CatalogItem> tree = new BTree<>();

        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            if ((i & STEP_MASK) == 0 && cancelled.getAsBoolean()) return rows;
            try {
                tree.insert(keys[i], items.get(i));
            } catch (TreeOperationException e) {
                throw new IllegalStateException(
                        "insert failed for key " + keys[i], e);
            }
        }
        rows.add(new Row("Вставка", n, System.nanoTime() - t0));

        // ---- поиск ----
        t0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            if ((i & STEP_MASK) == 0 && cancelled.getAsBoolean()) return rows;
            try {
                if (tree.search(keys[i]) == null)
                    throw new IllegalStateException("search miss for key " + keys[i]);
            } catch (TreeOperationException e) {
                throw new IllegalStateException("search failed for key " + keys[i], e);
            }
        }
        rows.add(new Row("Поиск", n, System.nanoTime() - t0));

        // ---- диапазонный скан ----
        int lo = Math.min(keys[0], keys[n - 1]);
        int hi = Math.max(keys[0], keys[n - 1]);
        t0 = System.nanoTime();
        for (int r = 0; r < RANGE_SCAN_REPEATS; r++) {
            if (cancelled.getAsBoolean()) return rows;
            try {
                tree.rangeScan(lo, hi);
            } catch (TreeOperationException e) {
                throw new IllegalStateException(
                        "rangeScan failed for [" + lo + ".." + hi + "]", e);
            }
        }
        rows.add(new Row("Диапазонный скан ×" + RANGE_SCAN_REPEATS, n,
                System.nanoTime() - t0));

        // ---- статистика ----
        t0 = System.nanoTime();
        BTree.FillStats st = tree.stats();
        rows.add(new Row("Статистика", st.nodes(), System.nanoTime() - t0));

        // ---- удаление ----
        t0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            if ((i & STEP_MASK) == 0 && cancelled.getAsBoolean()) return rows;
            try {
                tree.delete(keys[i]);
            } catch (TreeOperationException e) {
                throw new IllegalStateException(
                        "delete failed for key " + keys[i], e);
            }
        }
        rows.add(new Row("Удаление", n, System.nanoTime() - t0));

        return rows;
    }
    
    public record SpeedRow(int elements, int nodes, double insertMillis,
                           double averageInsertMicros, double searchMicros) {}

    public static SpeedRow runSpeed(int n, long seed, BooleanSupplier cancelled) {
        int[] keys = randomKeys(n, seed);
        List<CatalogItem> items = makeItems(keys, seed);
        BTree<CatalogItem> tree = new BTree<>();

        long started = System.nanoTime();
        for (int i = 0; i < n; i++) {
            if ((i & STEP_MASK) == 0 && cancelled.getAsBoolean()) return null;
            try {
                tree.insert(keys[i], items.get(i));
            } catch (TreeOperationException e) {
                throw new IllegalStateException("Ошибка вставки: " + keys[i], e);
            }
        }
        long insertNanos = System.nanoTime() - started;
        if (cancelled.getAsBoolean()) return null;

        // Один и тот же существующий артикул, многократный поиск для точности.
        int target = keys[n / 2];
        final int repeats = 20_000;
        for (int i = 0; i < 2_000; i++) {
            try {
                if (tree.search(target) == null) throw new IllegalStateException("Ключ не найден");
            } catch (TreeOperationException e) {
                throw new IllegalStateException("Ошибка поиска", e);
            }
        }
        started = System.nanoTime();
        for (int i = 0; i < repeats; i++) {
            if ((i & STEP_MASK) == 0 && cancelled.getAsBoolean()) return null;
            try {
                if (tree.search(target) == null) throw new IllegalStateException("Ключ не найден");
            } catch (TreeOperationException e) {
                throw new IllegalStateException("Ошибка поиска", e);
            }
        }
        long searchNanos = System.nanoTime() - started;
        return new SpeedRow(n, tree.stats().nodes(),
                insertNanos / 1_000_000.0,
                insertNanos / (double) n / 1_000.0,
                searchNanos / (double) repeats / 1_000.0);
    }

    static int[] randomKeys(int n, long seed) {
        Random rnd = new Random(seed);
        int[] keys = new int[n];
        HashSet<Integer> used = new HashSet<>(n * 2);
        for (int i = 0; i < n; i++) {
            int k;
            do { k = rnd.nextInt(KEY_BOUND) + 1; } while (!used.add(k));
            keys[i] = k;
        }
        return keys;
    }

    /** Отдаём генерацию элементов в {@link DataGenerator}, чтобы распределение было единым. */
    static List<CatalogItem> makeItems(int[] keys, long seed) {
        Random rnd = new Random(seed ^ 0x9E3779B9L);
        List<CatalogItem> out = new ArrayList<>(keys.length);
        for (int k : keys) out.add(DataGenerator.randomItem(k, rnd));
        return out;
    }
}