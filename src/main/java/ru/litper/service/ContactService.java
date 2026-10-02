package ru.litper.service;

import ru.litper.model.Contact;
import ru.litper.structure.KeyExtractor;
import ru.litper.structure.Trie;

import java.util.Locale;

/**
 * Справочник контактов: хранилище и операции над ним.
 *
 * <p>Основное хранилище — собственное префиксное дерево {@link Trie} по имени
 * (начиная с лабораторной №2 списки как хранилище запрещены). Ключ индексации —
 * имя в нижнем регистре, поэтому поиск и автодополнение не зависят от регистра,
 * а в дереве попадают вместе «Иван» и «ИВАН».</p>
 *
 * <p>Важно: класс {@link Contact} изменяемый, поэтому имя контакта, уже лежащего
 * в дереве, нельзя менять напрямую — индекс разойдётся с данными. Для правки имени
 * используйте {@link #update(Contact, Contact)}.</p>
 *
 * <p>Класс не зависит от JavaFX: фоновые потоки пишут в дерево, GUI-поток читает
 * снимки {@link #getAll()}.</p>
 */
public class ContactService {

    /** Ключ индексации: имя приводится к нижнему регистру. */
    private static final KeyExtractor<Contact> BY_NAME =
            contact -> contact.getName().toLowerCase(Locale.ROOT);

    private final Trie<Contact> index = new Trie<>(Contact.class, BY_NAME);

    /** Само дерево — нужно визуализации. Наружу отдаётся только для чтения. */
    public Trie<Contact> index() {
        return index;
    }

    /** Алфавитный снимок всех контактов (неизменяемый массив). */
    public Contact[] getAll() {
        return index.toArray();
    }

    public void add(Contact contact) {
        index.insert(contact);
    }

    /** Добавляет сгенерированный или загруженный набор контактов. */
    public void addAll(Contact[] contacts) {
        for (Contact contact : contacts) {
            index.insert(contact);
        }
    }

    /**
     * Заменяет контакт (в том числе при смене имени: старое имя удаляется из индекса).
     *
     * @param original контакт, сейчас лежащий в дереве
     * @param updated  новая версия контакта
     * @return {@code true}, если исходный контакт найден и заменён
     */
    public boolean update(Contact original, Contact updated) {
        if (!index.remove(original)) {
            return false;
        }
        index.insert(updated);
        return true;
    }

    public boolean remove(Contact contact) {
        return index.remove(contact);
    }

    /** Полностью заменяет содержимое справочника (загрузка CSV, генерация). */
    public void replaceAll(Contact[] contacts) {
        index.clear();
        addAll(contacts);
    }

    public void clear() {
        index.clear();
    }

    public int size() {
        return index.size();
    }

    /** Точный поиск по имени (регистр не важен). */
    public Contact[] findByName(String name) {
        return index.findAll(normalize(name));
    }

    /** Автодополнение по префиксу имени. */
    public Contact[] suggest(String prefix, int limit) {
        return index.autocomplete(normalize(prefix), limit);
    }

    /** Сколько контактов в поддереве префикса. */
    public int countByPrefix(String prefix) {
        return index.countByPrefix(normalize(prefix));
    }

    /** Удаляет все контакты, имена которых начинаются с префикса. */
    public int removeByPrefix(String prefix) {
        String normalized = normalize(prefix);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Префикс не должен быть пустым");
        }
        return index.removePrefix(normalized);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}