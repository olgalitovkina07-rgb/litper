package ru.litper.persistence;

import ru.litper.model.Contact;

/**
 * Результат загрузки CSV: успешно разобранные контакты и число пропущенных строк.
 *
 * <p>Контакты отдаются массивом, а не списком: начиная с лабораторной №2
 * список не используется как хранилище данных.</p>
 */
public record CsvLoadResult(Contact[] contacts, int skippedLines) {
}