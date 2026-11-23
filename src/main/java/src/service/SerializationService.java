package src.service;

import javafx.concurrent.Task;
import src.model.CatalogItem;
import src.serializable.Serializer;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;

public final class SerializationService {

    public Task<Serializer.LoadResult<List<CatalogItem>>> loadTask(
            Serializer<List<CatalogItem>> serializer, Path path) {
        return new Task<>() {
            @Override protected Serializer.LoadResult<List<CatalogItem>> call() throws Exception {
                updateMessage("Импорт " + serializer.format() + "…");
                if (isCancelled())
                    throw new CancellationException();

                var result = serializer.load(path);
                if (isCancelled())
                    throw new CancellationException();

                updateProgress(1, 1);
                return result;
            }
        };
    }

    public Task<Void> saveTask(Serializer<List<CatalogItem>> serializer,
                               List<CatalogItem> snapshot, Path path, int targetVersion) {
        return new Task<>() {
            @Override protected Void call() throws Exception {
                updateMessage("Экспорт " + serializer.format() + " v" + targetVersion + "…");
                if (isCancelled())
                    throw new CancellationException();

                serializer.save(snapshot, path, targetVersion);
                updateProgress(1, 1);
                return null;
            }
        };
    }

}