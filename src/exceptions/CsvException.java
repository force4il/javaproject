package exceptions;

/** Базовое исключение при работе с csv */
public class CsvException extends AppException {

    public CsvException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public CsvException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}