package exceptions;

/** Базовое проверяемое исключение для операций над деревом*/
public class TreeException extends AppException {

    public TreeException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public TreeException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}