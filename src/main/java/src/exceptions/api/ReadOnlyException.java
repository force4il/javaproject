package src.exceptions.api;

public class ReadOnlyException extends ApiException {
    public ReadOnlyException(String message) {
        super(422, "READ_ONLY", message);
    }
}