package ru.litper.server.error;

/**
 * Некорректный запрос: неизвестный формат, пустое тело и т. п.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
