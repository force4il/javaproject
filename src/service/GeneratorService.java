package service;

import exceptions.TreeOperationException;
import model.CatalogItem;

import java.util.HashSet;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/** Генерация набора CatalogItem в дерево */
public final class GeneratorService {

    private static final int KEY_RANGE_FACTOR = 10;
    private static final int PROGRESS_MASK = 0xFF;
    public static final int MAX_COUNT = 70000;

    public int generate(TreeService tree, int count, long seed,
                        IntConsumer progress, BooleanSupplier cancel) {
        if (count <= 0 || count > MAX_COUNT)
            throw new IllegalArgumentException(
                    "count должен быть в диапазоне [1.." + MAX_COUNT + "], получено " + count);

        Random rnd = new Random(seed);
        HashSet<Integer> used = new HashSet<>(count * 2);
        int inserted = 0;

        for (int i = 0; i < count; i++) {
            if (cancel != null && cancel.getAsBoolean())
                return inserted;

            int num;
            do {
                num = rnd.nextInt(Math.max(1, count * KEY_RANGE_FACTOR)) + 1;
            } while (!used.add(num));

            CatalogItem it = DataGenerator.randomItem(num, rnd);
            try {
                if (tree.insertIfAbsent(it))
                    inserted++;
            } catch (TreeOperationException e) {
                // генератор создаёт ключи > 0; эта ветка — признак бага
                throw new IllegalStateException(
                        "generator produced invalid key: " + num, e);
            }

            if (progress != null && (i & PROGRESS_MASK) == 0)
                progress.accept(i);
        }
        return inserted;
    }
}