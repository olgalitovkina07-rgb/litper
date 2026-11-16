package ru.litper.service;

/**
 * Контакт с таким же именем и телефоном уже есть в справочнике.
 */
public class DuplicateContactException extends RuntimeException {

    public DuplicateContactException(String message) {
        super(message);
    }
}
