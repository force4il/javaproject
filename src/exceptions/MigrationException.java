package exceptions;

import java.io.IOException;

public class MigrationException extends IOException {
    public enum Reason { FUTURE_VERSION, UNSUPPORTED_VERSION, CORRUPTED, BAD_FIELD }

    private final Reason reason;

    public MigrationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public MigrationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
}