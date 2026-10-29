package exceptions;

/** Собственные номера исключений */
public enum ErrorCode {

    FILE_READ("CSV_001", "Ошибка чтения файла"),
    FILE_WRITE("CSV_002", "Ошибка записи файла"),
    BAD_FIELD_COUNT("CSV_003", "Неверное количество полей"),
    UNKNOWN_TYPE("CSV_004", "Неизвестный тип записи"),
    BAD_NUMBER("CSV_005", "Поле не является числом"),
    BAD_DATE("CSV_006", "Поле не является датой"),
    BAD_FIELD("CSV_007", "Обязательное поле не заполнено"),
    UNCLOSED_QUOTE("CSV_008", "Незакрытая кавычка"),
    PARSE_ERROR("CSV_009", "Некорректный формат данных"),
    BAD_INT_RANGE("CSV_010", "Число не помещается в int"),
    TREE_DUPLICATE_KEY("TREE_001", "Ключ уже существует в дереве"),
    TREE_KEY_NOT_FOUND("TREE_002", "Ключ не найден в дереве"),
    TREE_INVALID_KEY("TREE_003", "Недопустимое значение ключа"),
    TREE_INVALID_RANGE("TREE_004", "Некорректный диапазон (from > to)"),
    TREE_EMPTY("TREE_005", "Дерево пусто"),
    TREE_CAPACITY("TREE_006", "Превышена ёмкость узла дерева"),
    TREE_STATE_CORRUPTED("TREE_007", "Нарушен инвариант B-дерева"),
    TREE_OPERATION_CANCELLED("TREE_008", "Операция над деревом отменена");

    private final String code;
    private final String description;

    ErrorCode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() { return code; }
    public String getDescription() { return description; }
}