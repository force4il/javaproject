package service;

import csv.CsvLoader;
import csv.CsvSave;
import exceptions.CsvParseException;
import exceptions.ErrorCode;
import exceptions.TreeOperationException;
import javafx.concurrent.Task;
import model.CatalogItem;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

/** Фоновые задачи для загрузки/сохранения CSV */
public final class FileService {

    public Task<CsvLoader.LoadResult> loadTask(Path file) {
        return new Task<>() {
            @Override
            protected CsvLoader.LoadResult call() throws Exception {
                updateMessage("Чтение CSV...");
                CsvLoader.LoadResult result = new CsvLoader().load(file);
                if (isCancelled())
                    throw new CancellationException("загрузка отменена");

                TreeService temporary = new TreeService();
                List<CatalogItem> accepted = new ArrayList<>();
                List<CsvLoader.SkippedRow> skipped = new ArrayList<>(result.skippedRows());
                int total = result.items().size();

                updateMessage("Проверка записей...");
                for (int i = 0; i < total; i++) {
                    if (isCancelled())
                        throw new CancellationException("загрузка отменена");

                    CatalogItem item = result.items().get(i);
                    try {
                        if (temporary.insertIfAbsent(item)) {
                            accepted.add(item);
                        } else {
                            skipped.add(new CsvLoader.SkippedRow(-1,
                                    new CsvParseException(ErrorCode.TREE_DUPLICATE_KEY,
                                            "Повторный артикул: " + item.getItemNumber())));
                        }
                    } catch (TreeOperationException ex) {
                        skipped.add(new CsvLoader.SkippedRow(-1,
                                new CsvParseException(ErrorCode.PARSE_ERROR,
                                        ex.getMessage(), ex)));
                    }
                    updateProgress(i + 1, total);
                }

                return new CsvLoader.LoadResult(
                        List.copyOf(accepted), List.copyOf(skipped));
            }
        };
    }

    public Task<Void> saveTask(List<CatalogItem> snapshot, Path file) {
        return new Task<>() {
            @Override protected Void call() throws Exception {
                CsvSave.save(snapshot, file);
                return null;
            }
        };
    }
}