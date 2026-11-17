package ru.litper.server.error;

import java.util.List;

/**
 * Некорректные данные контакта, не прошедшие доменную валидацию.
 */
public class InvalidContactException extends RuntimeException {

    private final List<String> errors;

    public InvalidContactException(String message) {
        this(message, List.of(message));
    }

    public InvalidContactException(String message, List<String> errors) {
        super(message);
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
