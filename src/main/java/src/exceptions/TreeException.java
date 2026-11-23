package src.exceptions;

import java.io.Serial;

/** Базовое исключение для операций над деревом*/
public class TreeException extends AppException {
    @Serial
    private static final long serialVersionUID = 1L;

    public TreeException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public TreeException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}