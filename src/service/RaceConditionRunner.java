package service;

import exceptions.ErrorCode;
import exceptions.TreeOperationException;
import tree.BTree;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Общая логика конкурентной вставки одного ключа в B-дерево*/
public final class RaceConditionRunner {

    /** Результат одного прогона */
    public record Result(int threadCount, int key, boolean useSync,
            int successes, int duplicates, int errors,
            boolean invariantsValid, boolean keyPresent,
            int actualSize, String invariantProblem,
            Throwable firstError
    ) {
        public boolean isCorrect(int expectedSize, int expectedInsertions, int expectedDuplicates) {
            return invariantsValid && keyPresent && actualSize == expectedSize
                    && successes == expectedInsertions && duplicates == expectedDuplicates
                    && errors == 0;
        }
    }

    private RaceConditionRunner() {}

    public static <T> Result run(BTree<T> tree, int key, T value,
                                 int threadCount, boolean useSync)
            throws InterruptedException {

        // включаем задержку, если режим без синхронизации
        if (!useSync) {
            BTree.RACE_DELAY_NANOS = 10000000; // 10 мс
        } else {
            BTree.RACE_DELAY_NANOS = 0;
        }

        try {
            final Object lock = new Object();
            final AtomicInteger successes = new AtomicInteger();
            final AtomicInteger duplicates = new AtomicInteger();
            final AtomicInteger errors = new AtomicInteger();
            final AtomicReference<Throwable> firstError = new AtomicReference<>();

            final CountDownLatch ready = new CountDownLatch(threadCount);
            final CountDownLatch start = new CountDownLatch(1);
            final CountDownLatch done = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int threadNumber = i;
                Thread worker = new Thread(() -> {
                    try {
                        ready.countDown();
                        start.await();                      // общий старт
                        try {
                            if (useSync) {
                                synchronized (lock) {
                                    tree.insert(key, value);
                                }
                            } else {
                                tree.insert(key, value);
                            }
                            successes.incrementAndGet();
                        } catch (TreeOperationException ex) {
                            if (ex.getErrorCode() == ErrorCode.TREE_DUPLICATE_KEY) {
                                duplicates.incrementAndGet();
                            } else {
                                errors.incrementAndGet();
                                firstError.compareAndSet(null, ex);
                            }
                        } catch (Throwable ex) {
                            errors.incrementAndGet();
                            firstError.compareAndSet(null, ex);
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        errors.incrementAndGet();
                        firstError.compareAndSet(null, ex);
                    } finally {
                        done.countDown();
                    }
                }, "race-insert-" + threadNumber);

                worker.setDaemon(true);
                worker.start();
            }

            try {
                ready.await();                              // все потоки готовы
            } finally {
                start.countDown();                          // синхронный старт
            }
            done.await();                                   // ждём завершения

            return collect(tree, key, threadCount, useSync,
                    successes, duplicates, errors, firstError);

        } finally {
            // сбрасываем задержку, даже если было исключение
            BTree.RACE_DELAY_NANOS = 0;
        }
    }

    private static <T> Result collect(BTree<T> tree, int key, int threadCount, boolean useSync,
                                      AtomicInteger successes, AtomicInteger duplicates,
                                      AtomicInteger errors, AtomicReference<Throwable> firstError) {
        boolean valid = true;
        boolean keyPresent = false;
        String problem = "";
        try {
            tree.validateInvariants();
            keyPresent = tree.search(key) != null;
        } catch (Throwable ex) {
            valid = false;
            problem = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        }
        return new Result(threadCount, key, useSync,
                successes.get(), duplicates.get(), errors.get(),
                valid, keyPresent, tree.size(), problem, firstError.get());
    }
}