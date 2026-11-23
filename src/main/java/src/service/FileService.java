package src.service;

import src.csv.CsvLoader;
import src.csv.CsvSave;
import src.csv.CsvSchema;
import src.exceptions.CsvException;
import src.exceptions.CsvParseException;
import src.exceptions.ErrorCode;
import src.exceptions.TreeOperationException;
import javafx.concurrent.Task;
import src.model.CatalogItem;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

/** Фоновые задачи для загрузки/сохранения CSV. */
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
                        if (temporary.insertIfAbsent(item))
                            accepted.add(item);
                        else
                            skipped.add(new CsvLoader.SkippedRow(-1,
                                    new CsvParseException(ErrorCode.TREE_DUPLICATE_KEY,
                                            "Повторный артикул: " + item.getItemNumber())));
                    } catch (TreeOperationException ex) {
                        skipped.add(new CsvLoader.SkippedRow(-1,
                                new CsvParseException(ErrorCode.PARSE_ERROR, ex.getMessage(), ex)));
                    }
                    updateProgress(i + 1, total);
                }

                return new CsvLoader.LoadResult(List.copyOf(accepted), List.copyOf(skipped), result.sourceVersion());
            }
        };
    }

    public Task<Void> saveTask(List<CatalogItem> snapshot, Path file, int targetVersion) {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Сохранение CSV v" + targetVersion + "...");
                if (isCancelled())
                    throw new CancellationException("сохранение отменено");

                try {
                    CsvSave.save(snapshot, file, targetVersion);
                } catch (CsvException ex) {
                    throw ex;
                }

                if (isCancelled())
                    throw new CancellationException("сохранение отменено");

                updateProgress(1, 1);
                return null;
            }
        };
    }
}