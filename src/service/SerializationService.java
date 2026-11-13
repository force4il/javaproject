package service;

import javafx.concurrent.Task;
import model.CatalogItem;
import serializable.Serializer;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;

public final class SerializationService {

    public Task<Void> saveTask(Serializer<List<CatalogItem>> serializer, List<CatalogItem> snapshot, Path path) {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Экспорт " + serializer.format() + "…");
                if (isCancelled())
                    throw new CancellationException();

                serializer.save(snapshot, path);
                updateProgress(1, 1);
                return null;
            }
        };
    }

    public Task<List<CatalogItem>> loadTask(Serializer<List<CatalogItem>> serializer, Path path) {
        return new Task<>() {
            @Override
            protected List<CatalogItem> call() throws Exception {
                updateMessage("Импорт " + serializer.format() + "…");
                if (isCancelled())
                    throw new CancellationException();

                List<CatalogItem> result = serializer.load(path);
                if (isCancelled())
                    throw new CancellationException();

                updateProgress(1, 1);
                return result;
            }
        };
    }
}