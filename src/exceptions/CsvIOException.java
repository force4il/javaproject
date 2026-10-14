package exceptions;

/** Ошибка ввода/вывода */
public class CsvIOException extends CsvException {

    public CsvIOException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public CsvIOException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}