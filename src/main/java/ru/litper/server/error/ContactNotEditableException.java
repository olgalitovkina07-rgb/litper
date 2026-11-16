package ru.litper.server.error;

/**
 * Попытка изменить контакт, который не реализует {@code Editable}.
 */
public class ContactNotEditableException extends RuntimeException {

    public ContactNotEditableException(long id) {
        super("Контакт с ID " + id + " является read-only и не может быть изменён");
    }
}
