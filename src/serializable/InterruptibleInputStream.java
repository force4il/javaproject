
package serializable;

import java.io.*;
import java.util.concurrent.CancellationException;

final class InterruptibleInputStream
        extends FilterInputStream {

    InterruptibleInputStream(InputStream in) {
        super(in);
    }

    private void check() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException(
                    "Чтение отменено");
        }
    }

    @Override
    public int read() throws IOException {
        check();
        return super.read();
    }

    @Override
    public int read(byte[] b, int off, int len)
            throws IOException {
        check();
        return super.read(b, off, len);
    }
}
