package exceptions;

/** Ошибка парсинга строки */
public class CsvParseException extends CsvException {

    public CsvParseException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public CsvParseException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}