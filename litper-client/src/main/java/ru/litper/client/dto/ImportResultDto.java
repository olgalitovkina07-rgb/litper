package ru.litper.client.dto;

public record ImportResultDto(
        String format,
        int sourceVersion,
        int currentVersion,
        int contacts,
        boolean migrated,
        int skippedLines) {
}
