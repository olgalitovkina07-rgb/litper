package ru.litper.server.dto;

/**
 * Количество контактов в поддереве префикса.
 */
public record CountResponse(String prefix, int count) {
}
