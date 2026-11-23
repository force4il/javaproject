package src.server;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import src.exceptions.TreeOperationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TreeOperationException.class)
    public ProblemDetail handleTreeOperation(TreeOperationException ex) {
        HttpStatus status = switch (ex.getErrorCode()) {
            case TREE_KEY_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case TREE_DUPLICATE_KEY -> HttpStatus.CONFLICT;
            case TREE_INVALID_KEY, TREE_INVALID_RANGE -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        pd.setTitle("Ошибка операции с B-деревом");
        pd.setProperty("code", ex.getErrorCode().name());
        return pd;
    }
}