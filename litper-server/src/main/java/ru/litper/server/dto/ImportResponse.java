package ru.litper.server.dto;

/**
 * Итоги импорта справочника через REST API.
 */
public record ImportResponse(
        String format,
        int sourceVersion,
        int currentVersion,
        int contacts,
        boolean migrated,
        int skippedLines) {
}
