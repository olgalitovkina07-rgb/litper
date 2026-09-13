package ru.litper.persistence;

import ru.litper.model.Contact;

import java.util.List;

/**
 * Результат загрузки CSV: успешно разобранные контакты и число пропущенных строк.
 */
public record CsvLoadResult(List<Contact> contacts, int skippedLines) {
}