package exceptions;

import java.io.Serial;

/** Нарушение внутреннего инварианта B-дерева */
public class TreeStateException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;
    private final ErrorCode errorCode;

    public TreeStateException(ErrorCode errorCode, String message) {
        super("[" + errorCode.getCode() + "] " + errorCode.getDescription() + ": " + message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() { return errorCode; }
    public String getCode() { return errorCode.getCode(); }
}