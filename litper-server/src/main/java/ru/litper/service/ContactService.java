package ru.litper.service;

import org.springframework.stereotype.Service;
import ru.litper.model.Contact;
import ru.litper.structure.KeyExtractor;
import ru.litper.structure.Trie;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Справочник контактов: хранилище и операции над ним.
 *
 * <p>Основное хранилище — собственное префиксное дерево {@link Trie} по имени.
 * Ключ индексации — имя в нижнем регистре, поэтому поиск и автодополнение не
 * зависят от регистра. Для REST API сервис дополнительно хранит числовые ID
 * контактов, не меняя модель и файловые форматы.</p>
 *
 * <p>Класс является Spring-бином и singleton-хранилищем: REST-контроллеры и
 * JavaFX GUI работают с одним и тем же экземпляром.</p>
 */
@Service
public class ContactService {

    /** Ключ индексации: имя приводится к нижнему регистру. */
    private static final KeyExtractor<Contact> BY_NAME =
            contact -> contact.getName().toLowerCase(Locale.ROOT);

    private final Trie<Contact> index = new Trie<>(Contact.class, BY_NAME);
    private final Map<Long, Contact> contactsById = new HashMap<>();
    private final Map<Contact, Long> idsByContact = new IdentityHashMap<>();
    private final List<ContactChangeListener> listeners = new CopyOnWriteArrayList<>();

    private long nextId = 1;

    /** Само дерево — нужно визуализации. Наружу отдаётся только для чтения. */
    public Trie<Contact> index() {
        return index;
    }

    /** Алфавитный снимок всех контактов. */
    public synchronized Contact[] getAll() {
        return index.toArray();
    }

    /** Снимок контактов вместе с их ID для REST-слоя. */
    public synchronized ContactEntry[] entries() {
        Contact[] contacts = index.toArray();
        ContactEntry[] result = new ContactEntry[contacts.length];
        for (int i = 0; i < contacts.length; i++) {
            result[i] = new ContactEntry(idOfInternal(contacts[i]), contacts[i]);
        }
        return result;
    }

    /**
     * Добавляет контакт и возвращает его ID.
     */
    public synchronized long add(Contact contact) {
        Objects.requireNonNull(contact, "Контакт не должен быть null");
        long id = assignId(contact);
        index.insert(contact);
        fireChanged();
        return id;
    }

    /** Добавляет сгенерированный или загруженный набор контактов. */
    public synchronized void addAll(Contact[] contacts) {
        insertAll(contacts);
        fireChanged();
    }

    /**
     * Добавляет контакт, если такой же имя+телефон ещё не заняты.
     *
     * @throws DuplicateContactException если найден дубликат
     */
    public synchronized long addUnique(Contact contact) {
        ensureNoDuplicate(contact, null);
        return add(contact);
    }

    /**
     * Заменяет контакт (в том числе при смене имени: старое имя удаляется из индекса).
     *
     * @param original контакт, сейчас лежащий в дереве
     * @param updated  новая версия контакта
     * @return {@code true}, если исходный контакт найден и заменён
     */
    public synchronized boolean update(Contact original, Contact updated) {
        Objects.requireNonNull(updated, "Контакт не должен быть null");
        if (!index.remove(original)) {
            return false;
        }
        Long id = idsByContact.remove(original);
        if (id == null) {
            id = nextId++;
        }
        contactsById.put(id, updated);
        idsByContact.put(updated, id);
        index.insert(updated);
        fireChanged();
        return true;
    }

    /**
     * Обновляет контакт, проверяя, что новые имя+телефон не совпадают с другим.
     *
     * @throws DuplicateContactException если найден дубликат
     */
    public synchronized boolean updateUnique(Contact original, Contact updated) {
        ensureNoDuplicate(updated, original);
        return update(original, updated);
    }

    public synchronized boolean remove(Contact contact) {
        if (!index.remove(contact)) {
            return false;
        }
        removeId(contact);
        fireChanged();
        return true;
    }

    /** Полностью заменяет содержимое справочника (загрузка CSV, генерация, импорт). */
    public synchronized void replaceAll(Contact[] contacts) {
        index.clear();
        contactsById.clear();
        idsByContact.clear();
        nextId = 1;
        insertAll(contacts);
        fireChanged();
    }

    public synchronized void clear() {
        index.clear();
        contactsById.clear();
        idsByContact.clear();
        nextId = 1;
        fireChanged();
    }

    public synchronized int size() {
        return index.size();
    }

    /** Контакт по ID или {@code null}, если запись не найдена. */
    public synchronized Contact getById(long id) {
        return contactsById.get(id);
    }

    /** ID контакта; для старых записей назначается при первом обращении. */
    public synchronized long idOf(Contact contact) {
        return idOfInternal(contact);
    }

    /** Точный поиск по имени (регистр не важен). */
    public synchronized Contact[] findByName(String name) {
        return index.findAll(normalize(name));
    }

    /** Автодополнение по префиксу имени. */
    public synchronized Contact[] suggest(String prefix, int limit) {
        return index.autocomplete(normalize(prefix), limit);
    }

    /** Сколько контактов в поддереве префикса. */
    public synchronized int countByPrefix(String prefix) {
        return index.countByPrefix(normalize(prefix));
    }

    /** Удаляет все контакты, имена которых начинаются с префикса. */
    public synchronized int removeByPrefix(String prefix) {
        String normalized = normalize(prefix);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Префикс не должен быть пустым");
        }
        Contact[] affected = index.autocomplete(normalized, Trie.NO_LIMIT);
        int removed = index.removePrefix(normalized);
        if (removed > 0) {
            for (Contact contact : affected) {
                removeId(contact);
            }
            fireChanged();
        }
        return removed;
    }

    public void addChangeListener(ContactChangeListener listener) {
        listeners.add(Objects.requireNonNull(listener, "Слушатель не должен быть null"));
    }

    public void removeChangeListener(ContactChangeListener listener) {
        listeners.remove(listener);
    }

    private void insertAll(Contact[] contacts) {
        for (Contact contact : contacts) {
            Objects.requireNonNull(contact, "Контакт не должен быть null");
            assignId(contact);
            index.insert(contact);
        }
    }

    private long assignId(Contact contact) {
        long id = nextId++;
        Long previous = idsByContact.put(contact, id);
        if (previous != null) {
            contactsById.remove(previous);
        }
        contactsById.put(id, contact);
        return id;
    }

    private long idOfInternal(Contact contact) {
        Long id = idsByContact.get(contact);
        if (id == null) {
            id = assignId(contact);
        }
        return id;
    }

    private void removeId(Contact contact) {
        Long id = idsByContact.remove(contact);
        if (id != null) {
            contactsById.remove(id);
        }
    }

    private void ensureNoDuplicate(Contact candidate, Contact exclude) {
        Contact duplicate = findDuplicate(candidate, exclude);
        if (duplicate != null) {
            throw new DuplicateContactException(
                    "Контакт с именем «" + candidate.getName()
                            + "» и телефоном " + candidate.getPhone() + " уже существует");
        }
    }

    private Contact findDuplicate(Contact candidate, Contact exclude) {
        Contact[] sameName = index.findAll(normalize(candidate.getName()));
        for (Contact existing : sameName) {
            if (existing != exclude
                    && normalize(existing.getPhone()).equals(normalize(candidate.getPhone()))) {
                return existing;
            }
        }
        return null;
    }

    private void fireChanged() {
        for (ContactChangeListener listener : listeners) {
            listener.onContactsChanged();
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /** Пара «ID + контакт» для REST-представления. */
    public record ContactEntry(long id, Contact contact) {
    }
}
