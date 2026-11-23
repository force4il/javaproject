package src.exceptions;

import java.io.Serial;

/** Базовое исключение при работе с csv */
public class CsvException extends AppException {
    @Serial
    private static final long serialVersionUID = 1L;

    public CsvException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public CsvException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}