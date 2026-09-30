package exceptions;

public enum ErrorCode {
    FILE_READ("CSV_001", "Ошибка чтения файла"),
    FILE_WRITE("CSV_002", "Ошибка записи файла"),
    BAD_FIELD_COUNT("CSV_003", "Неверное количество полей"),
    UNKNOWN_TYPE("CSV_004", "Неизвестный тип записи"),
    BAD_NUMBER("CSV_005", "Поле не является числом"),
    BAD_DATE("CSV_006", "Поле не является датой"),
    BAD_FIELD("CSV_007", "Обязательное поле не заполнено"),
    UNCLOSED_QUOTE("CSV_008", "Незакрытая кавычка"),
    PARSE_ERROR("CSV_009", "Некорректный формат данных");

    private final String code;
    private final String description;

    ErrorCode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() { return code; }
    public String getDescription() { return description; }
}