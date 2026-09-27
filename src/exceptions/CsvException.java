package exceptions;

/**базовый класс для исключений при работе с csv*/
public class CsvException extends Exception {
    private final ErrorCode errorCode;

    public CsvException(ErrorCode errorCode, String message) {
        super(format(errorCode, message));
        this.errorCode = errorCode;
    }

    public CsvException(ErrorCode errorCode, String message, Throwable cause) {
        super(format(errorCode, message), cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() { return errorCode; }
    public String getCode() { return errorCode.getCode(); }

    private static String format(ErrorCode code, String message) {
        return "[" + code.getCode() + "] " + code.getDescription() + ": " + message;
    }
}