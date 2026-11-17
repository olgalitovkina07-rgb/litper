package ru.litper.server.dto;

import org.springframework.http.MediaType;

/**
 * Готовый файл экспорта: содержимое, имя и MIME-тип.
 */
public record ExportedData(byte[] content, String fileName, MediaType mediaType) {
}
