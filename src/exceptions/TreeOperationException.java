package exceptions;

import java.io.Serial;

/** Пользовательская ошибка операции над деревом */
public class TreeOperationException extends TreeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public TreeOperationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public TreeOperationException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}