package serializable;

import java.io.IOException;
import java.nio.file.Path;

public interface Serializer<T> {
    int CURRENT_VERSION = CatalogCodec.CURRENT_VERSION;

    record Loaded<T>(T data, int version) {}

    String format();

    default void save(T data, Path file) throws IOException {
        save(data, file, CURRENT_VERSION);
    }

    void save(T data, Path file, int version) throws IOException;

    Loaded<T> load(Path file) throws IOException;
}