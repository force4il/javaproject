package src.exceptions.api;

public class FileStorageException extends ApiException {
    public FileStorageException(String message) {
        super(400, "FILE_ERROR", message);
    }
}