package src.exceptions.api;

import src.exceptions.ErrorCode;
import src.exceptions.tree.TreeOperationException;

import java.util.Map;

public class TreeApiException extends ApiException {

    private static final int DEFAULT_STATUS = 500;

    private static final Map<ErrorCode, Integer> STATUS = Map.of(
            ErrorCode.TREE_KEY_NOT_FOUND, 404,
            ErrorCode.TREE_DUPLICATE_KEY, 409,
            ErrorCode.TREE_INVALID_KEY,   400,
            ErrorCode.TREE_INVALID_RANGE, 400
    );

    public TreeApiException(TreeOperationException ex) {
        super(
                STATUS.getOrDefault(ex.getErrorCode(), DEFAULT_STATUS),
                ex.getErrorCode().name(),
                ex.getMessage()
        );
    }
}