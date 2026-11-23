package src.exceptions;

import java.io.Serial;

/** Ошибка парсинга строки */
public class CsvParseException extends CsvException {
    @Serial
    private static final long serialVersionUID = 1L;

    public CsvParseException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public CsvParseException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}