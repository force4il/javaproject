import exceptions.CsvException;

/**ошибка ввода/вывода*/
public class CsvIOException extends CsvException {
    public CsvIOException(String message, Throwable cause) { super(message, cause); }
}