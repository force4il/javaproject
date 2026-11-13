package serializable;

import java.io.IOException;
import java.nio.file.Path;

public interface Serializer<T> {
    String format();
    void save(T data, Path file) throws IOException;
    T load(Path file) throws IOException;
}