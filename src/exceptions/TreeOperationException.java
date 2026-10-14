package exceptions;

/** Пользовательская ошибка операции над деревом */
public class TreeOperationException extends TreeException {

    public TreeOperationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public TreeOperationException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}