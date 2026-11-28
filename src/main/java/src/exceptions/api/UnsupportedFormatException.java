package src.exceptions.api;

public class UnsupportedFormatException extends ApiException {
    public UnsupportedFormatException(String message) {
        super(400, "FORMAT", message);
    }
}