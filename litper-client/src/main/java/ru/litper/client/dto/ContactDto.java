package ru.litper.client.dto;

public record ContactDto(
        Long id,
        String type,
        String name,
        String phone,
        String email,
        String organization,
        String position,
        String internalNumber,
        String note,
        String tags) {
}
