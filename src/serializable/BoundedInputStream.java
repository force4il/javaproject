
package serializable;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Поток с ограничением общего количества прочитанных байтов.
 *
 * Позволяет обнаруживать превышение лимита,
 * даже если файл увеличился после первоначальной
 * проверки его размера.
 */
final class BoundedInputStream extends FilterInputStream {

    private final long maxBytes;
    private long consumed;

    BoundedInputStream(InputStream in, long maxBytes) {
        super(in);
        if (maxBytes < 0) {
            throw new IllegalArgumentException("Лимит не может быть отрицательным");
        }
        this.maxBytes = maxBytes;
    }

    @Override
    public int read() throws IOException {
        if (consumed == maxBytes) {
            if (in.read() == -1) {
                return -1;
            }

            throw new IOException(
                    "Размер входного файла превышает "
                            + maxBytes + " байт"
            );
        }

        int value = in.read();

        if (value != -1) {
            consumed++;
        }

        return value;
    }

    @Override
    public int read(byte[] bytes, int off, int len)
            throws IOException {

        Objects.checkFromIndexSize(off, len, bytes.length);

        if (len == 0) {
            return 0;
        }

        if (consumed == maxBytes) {
            if (in.read() == -1) {
                return -1;
            }

            throw new IOException(
                    "Размер входного файла превышает "
                            + maxBytes + " байт"
            );
        }

        int allowed = (int) Math.min(
                len,
                maxBytes - consumed
        );

        int n = in.read(bytes, off, allowed);

        if (n > 0) {
            consumed += n;
        }

        return n;
    }
}
