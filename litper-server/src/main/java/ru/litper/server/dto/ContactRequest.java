package ru.litper.server.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Тело запроса на создание или изменение контакта.
 */
public record ContactRequest(
        @NotBlank(message = "Тип контакта обязателен") String type,
        @NotBlank(message = "Имя обязательно") String name,
        @NotBlank(message = "Телефон обязателен") String phone,
        String email,
        String organization,
        String position,
        String internalNumber,
        String note,
        String tags) {
}
