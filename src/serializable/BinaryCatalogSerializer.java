
package serializable;

import model.CatalogItem;
import model.ProductWithWarranty;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;

public final class BinaryCatalogSerializer
        implements Serializer<List<CatalogItem>> {

    private static final int MAGIC = 0x42545245;
    private static final int MAX_STRING_BYTES =
            16 * 1024 * 1024;

    @Override
    public String format() {
        return "bin";
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException(
                    "Операция отменена"
            );
        }
    }

    private static void writeString(
            DataOutputStream out,
            String value
    ) throws IOException {

        byte[] bytes = value.getBytes(
                StandardCharsets.UTF_8
        );

        if (bytes.length > MAX_STRING_BYTES) {
            throw new IOException("Слишком длинная строка");
        }

        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(
            DataInputStream in
    ) throws IOException {

        int length = in.readInt();

        if (length < 0 || length > MAX_STRING_BYTES) {
            throw new IOException(
                    "Недопустимая длина строки: " + length
            );
        }

        byte[] bytes = new byte[length];
        in.readFully(bytes);

        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(
                            CodingErrorAction.REPORT
                    )
                    .onUnmappableCharacter(
                            CodingErrorAction.REPORT
                    )
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();

        } catch (CharacterCodingException ex) {
            throw new IOException(
                    "Некорректная кодировка UTF-8",
                    ex
            );
        }
    }

    @Override
    public void save(
            List<CatalogItem> data,
            Path file,
            int version
    ) throws IOException {

        CatalogCodec.requireValidVersion(version, "BIN");

        List<CatalogItem> items =
                CatalogCodec.checked(data);

        CatalogCodec.checkVersionItems(items, version);

        Path target = file.toAbsolutePath();
        Path parent = target.getParent();

        Path temp = Files.createTempFile(
                parent,
                ".catalog-",
                ".tmp"
        );

        try {
            try (DataOutputStream out =
                         new DataOutputStream(
                                 new BufferedOutputStream(
                                         Files.newOutputStream(temp)
                                 ))) {

                // Заголовок BIN-файла.
                out.writeInt(MAGIC);
                out.writeInt(version);
                out.writeInt(items.size());

                // В версии 3 записываем время экспорта.
                if (version == 3) {
                    out.writeLong(System.currentTimeMillis());
                }

                CRC32 crc = new CRC32();

                // В v3 контрольная сумма считается
                // по бинарным данным записей.
                DataOutputStream records = version == 3
                        ? new DataOutputStream(
                        new CheckedOutputStream(out, crc)
                )
                        : out;

                for (CatalogItem item : items) {
                    checkCancelled();

                    // В v1 тип объекта не записывается.
                    if (version >= 2) {
                        writeString(
                                records,
                                item.getType().getCsvName()
                        );
                    }

                    records.writeInt(item.getItemNumber());

                    writeString(
                            records,
                            item.getProductName()
                    );

                    writeString(
                            records,
                            item.getCategory()
                    );

                    records.writeInt(item.getPrice());
                    records.writeInt(item.getRemainder());

                    if (version >= 2
                            && item instanceof ProductWithWarranty warranty) {

                        writeString(
                                records,
                                warranty.getStartOfWarranty().toString()
                        );

                        records.writeInt(
                                warranty.getWarrantyMonths()
                        );
                    }
                }

                records.flush();

                // CRC32 хранится после всех записей.
                if (version == 3) {
                    out.writeLong(crc.getValue());
                }
            }

            checkCancelled();

            try {
                Files.move(
                        temp,
                        target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );

            } catch (AtomicMoveNotSupportedException ex) {
                throw new IOException(
                        "Файловая система не поддерживает "
                                + "атомарное сохранение",
                        ex
                );
            }

        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Override
    public Serializer.Loaded<List<CatalogItem>> load(
            Path file
    ) throws IOException {

        if (Files.size(file) > CatalogCodec.MAX_FILE_BYTES) {
            throw new IOException(
                    "BIN превышает лимит 256 МиБ"
            );
        }

        try (DataInputStream in =
                     new DataInputStream(
                             new BufferedInputStream(
                                     new InterruptibleInputStream(
                                             new BoundedInputStream(
                                                     Files.newInputStream(file),
                                                     CatalogCodec.MAX_FILE_BYTES
                                             )
                                     )
                             ))) {

            // Проверка сигнатуры файла.
            if (in.readInt() != MAGIC) {
                throw new IOException(
                        "Неверная сигнатура BIN"
                );
            }

            // Читаем версию.
            int version = in.readInt();

            CatalogCodec.requireValidVersion(
                    version,
                    "BIN"
            );

            int count = in.readInt();

            if (count < 0 || count > CatalogCodec.MAX_ITEMS) {
                throw new IOException(
                        "Недопустимое количество товаров"
                );
            }

            // Дополнительный заголовок v3.
            if (version == 3) {
                long timestamp = in.readLong();

                if (timestamp < 0) {
                    throw new IOException(
                            "Некорректная дата экспорта"
                    );
                }
            }

            CRC32 crc = new CRC32();

            DataInputStream records = version == 3
                    ? new DataInputStream(
                    new CheckedInputStream(in, crc)
            )
                    : in;

            List<CatalogItem> result =
                    new ArrayList<>(count);

            for (int i = 0; i < count; i++) {
                checkCancelled();

                String type = version == 1
                        ? "Product"
                        : readString(records);

                int id = records.readInt();

                String name = readString(records);
                String category = readString(records);

                int price = records.readInt();
                int remainder = records.readInt();

                String start = null;
                Integer months = null;

                if ("Warranty".equals(type)) {
                    start = readString(records);
                    months = records.readInt();
                }

                CatalogItem item = CatalogCodec.create(
                        type,
                        id,
                        name,
                        category,
                        price,
                        remainder,
                        start,
                        months
                );

                result.add(item);
            }

            // Проверка CRC32 версии 3.
            if (version == 3) {
                long expected = in.readLong();

                if (expected != crc.getValue()) {
                    throw new IOException(
                            "Нарушена целостность BIN (CRC32)"
                    );
                }
            }

            checkCancelled();

            // Проверяем отсутствие лишних байтов.
            if (in.read() != -1) {
                throw new IOException(
                        "Лишние данные в конце файла"
                );
            }

            List<CatalogItem> checked =
                    CatalogCodec.checked(result);

            CatalogCodec.checkVersionItems(
                    checked,
                    version
            );

            return new Serializer.Loaded<>(
                    checked,
                    version
            );

        } catch (EOFException ex) {
            throw new IOException(
                    "BIN-файл повреждён или обрезан",
                    ex
            );
        }
    }
}
