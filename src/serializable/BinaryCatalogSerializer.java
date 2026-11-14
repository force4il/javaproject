package serializable;

import model.CatalogItem;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;

public final class BinaryCatalogSerializer implements Serializer<List<CatalogItem>> {

    public static final int SCHEMA_VERSION = 1;
    private static final int HEADER_MAGIC = 0x42434154;

    @Override
    public String format() {
        return "bin";
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted())
            throw new CancellationException("Операция отменена");
    }

    @Override
    public void save(List<CatalogItem> data, Path file) throws IOException {
        List<CatalogItem> items = CatalogCodec.checked(data);

        Path target = file.toAbsolutePath();
        Path parent = target.getParent();
        Path temp = Files.createTempFile(parent, ".catalog-", ".tmp");
        try {
            try (ObjectOutputStream out = new ObjectOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(temp)))) {
                out.writeInt(HEADER_MAGIC);
                out.writeInt(SCHEMA_VERSION);

                for (CatalogItem ignored : items)
                    checkCancelled();

                out.writeObject(new ArrayList<>(items));
                out.flush();
            }

            checkCancelled();
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                throw new IOException("Файловая система не поддерживает атомарное сохранение", ex);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<CatalogItem> load(Path file) throws IOException {
        if (Files.size(file) > CatalogCodec.MAX_FILE_BYTES)
            throw new IOException("BIN превышает лимит 256 МиБ");

        try (InputStream raw = new InterruptibleInputStream(new BoundedInputStream(
                Files.newInputStream(file), CatalogCodec.MAX_FILE_BYTES))) {

            Object data = readObjectStream(raw);

            if (!(data instanceof List<?> list))
                throw new IOException("BIN-файл повреждён: ожидался список товаров");

            for (Object obj : list)
                if (!(obj instanceof CatalogItem))
                    throw new IOException("BIN-файл повреждён: элемент не является товаром");

            return CatalogCodec.checked((List<CatalogItem>) list);
        }
    }

    private static Object readObjectStream(InputStream raw) throws IOException {
        try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(raw))) {
            readAndValidateHeader(in);

            Object data;
            try {
                data = in.readObject();
            } catch (InvalidClassException ex) {
                throw new BinLoadException(
                        "BIN-файл несовместим с текущей версией приложения (не совпадает serialVersionUID). Пересохраните файл", ex);
            } catch (ClassNotFoundException ex) {
                throw new BinLoadException("BIN-файл повреждён: неизвестный класс", ex);
            }

            if (in.read() != -1)
                throw new BinLoadException("лишние данные в конце файла");

            CatalogCodec.checkCancelled();
            return data;

        } catch (BinLoadException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new IOException("BIN-файл повреждён", ex);
        }
    }

    public int getHeaderMagic() {
        return HEADER_MAGIC;
    }

    public int getSchemaVersion() {
        return SCHEMA_VERSION;
    }

    private static void readAndValidateHeader(ObjectInputStream in) throws IOException {
        int magic, version;
        try {
            magic = in.readInt();
            version = in.readInt();
        } catch (EOFException ex) {
            throw new BinLoadException("BIN-файл повреждён: заголовок обрезан", ex);
        } catch (IOException ex) {
            throw new BinLoadException(
                    "BIN-файл не содержит заголовка схемы (вероятно, создан старой версией приложения). "
                            + "Пересохраните файл.", ex);
        }

        if (magic != HEADER_MAGIC)
            throw new BinLoadException(
                    "BIN-файл не является каталогом: неверный заголовок "
                            + "(возможно, создан старой версией без заголовка; пересохраните файл)");

        if (version < 1)
            throw new BinLoadException("Некорректная версия схемы BIN: schema_version=" + version);

        if (version > SCHEMA_VERSION)
            throw new BinLoadException(
                    "BIN-файл создан в более новой версии приложения: "
                            + "schema_version=" + version + " > " + SCHEMA_VERSION
                            + ". Обновите приложение или сохраните файл в текущей версии.");

        if (version < SCHEMA_VERSION)
            throw new BinLoadException(
                    "BIN-файл версии " + version
                            + " не поддерживается текущей версией (ожидается " + SCHEMA_VERSION
                            + "). Требуется миграция.");
    }

    private static final class BinLoadException extends IOException {
        BinLoadException(String message) { super(message); }
        BinLoadException(String message, Throwable cause) { super(message, cause); }
    }
}