import exceptions.CsvException;

/**ошибка парсинга строки*/
public class CsvParseException extends CsvException {
    private final int lineNumber;

    public CsvParseException(int lineNumber, String message) {
        super("Строка " + lineNumber + ": " + message);
        this.lineNumber = lineNumber;
    }

    public CsvParseException(int lineNumber, String message, Throwable cause) {
        super("Строка " + lineNumber + ": " + message, cause);
        this.lineNumber = lineNumber;
    }

    public int getLineNumber() { return lineNumber; }
}