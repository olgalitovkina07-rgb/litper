package ru.litper.service;

import ru.litper.model.Contact;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Хранилище контактов и операции над ними.
 *
 * <p>Не зависит от JavaFX: GUI работает с сервисом, а не хранит бизнес-логику в контроллере.</p>
 */
public class ContactService {

    private final List<Contact> contacts = new ArrayList<>();

    /** Возвращает неизменяемое представление списка контактов. */
    public List<Contact> getAll() {
        return Collections.unmodifiableList(contacts);
    }

    public void add(Contact contact) {
        contacts.add(contact);
    }

    public void addAll(Collection<Contact> newContacts) {
        contacts.addAll(newContacts);
    }

    /** Заменяет контакт по позиции (используется при редактировании). */
    public void replace(int index, Contact replacement) {
        contacts.set(index, replacement);
    }

    /** Полностью заменяет содержимое справочника (используется при загрузке CSV). */
    public void replaceAll(Collection<Contact> newContacts) {
        contacts.clear();
        contacts.addAll(newContacts);
    }

    public void clear() {
        contacts.clear();
    }

    public int size() {
        return contacts.size();
    }
}