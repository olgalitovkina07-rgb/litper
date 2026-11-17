package ru.litper.persistence;

/**
 * Коды ошибок разбора строки CSV.
 */
public enum CsvErrorCode {

    /** Неверное количество полей в строке. */
    WRONG_FIELD_COUNT,
    /** Неизвестный тип контакта в первом поле. */
    UNKNOWN_TYPE,
    /** Поле не является числом, хотя должно им быть (внутренний номер). */
    BAD_NUMBER,
    /** Обязательное поле пустое. */
    EMPTY_FIELD
}