package ru.litper.server.dto;

/**
 * Необязательные фильтры выборки контактов.
 */
public record ContactFilter(
        String name,
        String organization,
        String type,
        String phone,
        String email,
        String note,
        String tags) {
}
