package ru.litper.persistence.serialize;

import java.io.IOException;

/**
 * Ошибка сохранения или загрузки справочника.
 *
 * <p>Отдельный тип нужен, чтобы отличить «файл повреждён или не того формата»
 * от обычных ошибок ввода-вывода и показать пользователю понятное сообщение.</p>
 */
public class SerializationException extends IOException {

    public SerializationException(String message) {
        super(message);
    }

    public SerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
