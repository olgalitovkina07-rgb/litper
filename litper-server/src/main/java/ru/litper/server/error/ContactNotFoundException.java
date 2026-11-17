package ru.litper.server.error;

/**
 * Контакт с указанным ID не найден.
 */
public class ContactNotFoundException extends RuntimeException {

    private final long id;

    public ContactNotFoundException(long id) {
        super("Контакт с ID " + id + " не найден");
        this.id = id;
    }

    public long getId() {
        return id;
    }
}
