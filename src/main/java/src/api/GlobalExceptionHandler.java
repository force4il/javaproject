package src.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MultipartException;

import src.exceptions.api.ApiException;
import src.exceptions.api.FileStorageException;
import src.exceptions.api.TreeApiException;
import src.exceptions.api.ValidationException;
import src.exceptions.tree.TreeOperationException;
import src.exceptions.MigrationException;

import java.io.IOException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(Exception e) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Unknown error: " + e.getMessage());
    }

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.valueOf(ex.status),
                ex.getMessage()
        );
        pd.setProperty("code", ex.code);
        return pd;
    }

    @ExceptionHandler(TreeOperationException.class)
    public ProblemDetail handleTreeException(TreeOperationException ex) {
        return handleApiException(new TreeApiException(ex));
    }

    @ExceptionHandler(MigrationException.class)
    public ProblemDetail handleMigrationException(MigrationException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage()
        );
        pd.setProperty("code", ex.getReason() == MigrationException.Reason.FUTURE_VERSION
                        ? "VERSION_TOO_NEW"
                        : ex.getReason().name()
        );
        return pd;
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            MultipartException.class,
            IllegalArgumentException.class
    })
    public ProblemDetail handleBadRequest(Exception ex) {
        return handleApiException(new ValidationException(ex.getMessage()));
    }

    @ExceptionHandler(IOException.class)
    public ProblemDetail handleIoException(IOException ex) {
        return handleApiException(new FileStorageException(
                ex.getMessage() == null ? "Ошибка файла" : ex.getMessage()
        ));
    }
}