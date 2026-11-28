package src.exceptions.api;

public class FileSizeException extends ApiException {
    public FileSizeException(String message) {
        super(400, "FILE_SIZE", message);
    }
}