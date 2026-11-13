package exceptions;

import java.io.Serial;

/** Ошибка ввода/вывода */
public class CsvIOException extends CsvException {
    @Serial
    private static final long serialVersionUID = 1L;

    public CsvIOException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public CsvIOException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}