package ru.litper.persistence;

/**
 * Ошибка разбора одной строки CSV.
 *
 * <p>Не прерывает загрузку файла: битые строки пропускаются,
 * ошибка каждой из них фиксируется отдельно.</p>
 */
public class CsvParseException extends Exception {

    private final CsvErrorCode code;
    private final long lineNumber;

    public CsvParseException(CsvErrorCode code, long lineNumber) {
        super("Строка " + lineNumber + ": " + describe(code));
        this.code = code;
        this.lineNumber = lineNumber;
    }

    public CsvErrorCode getCode() {
        return code;
    }

    public long getLineNumber() {
        return lineNumber;
    }

    private static String describe(CsvErrorCode code) {
        return switch (code) {
            case WRONG_FIELD_COUNT -> "неверное количество полей";
            case UNKNOWN_TYPE -> "неизвестный тип контакта";
            case BAD_NUMBER -> "ожидалось число";
            case EMPTY_FIELD -> "обязательное поле пустое";
        };
    }
}