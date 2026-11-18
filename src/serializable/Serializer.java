package serializable;

import java.io.IOException;
import java.nio.file.Path;

public interface Serializer<T> {
    String format();
    int currentVersion();

    LoadResult<T> load(Path file) throws IOException;

    default void save(T data, Path file) throws IOException {
        save(data, file, currentVersion());
    }

    void save(T data, Path file, int targetVersion) throws IOException;

    String describeDowngradeLoss(int targetVersion);

    record LoadResult<T>(T data, int sourceVersion, int targetVersion) {}
}