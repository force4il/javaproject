package src.exceptions.api;

public class ValidationException extends ApiException {
    public ValidationException(String message) {
        super(400, "VALIDATION", message);
    }
}