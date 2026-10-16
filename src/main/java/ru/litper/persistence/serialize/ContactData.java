package ru.litper.persistence.serialize;

import ru.litper.model.Contact;

/**
 * Содержимое файла справочника: версия схемы и список контактов.
 *
 * <p>Версия записывается всегда (требование на «отлично»): она нужна, чтобы при
 * чтении понять, какому формату соответствует файл. Пока версия одна, но в
 * лабораторной №4 на неё будет опираться миграция данных.</p>
 */
public record ContactData(int schemaVersion, ContactRecord[] contacts) {

    /** Текущая версия схемы файла. */
    public static final int CURRENT_VERSION = 1;

    /** Снимок контактов сервиса в виде данных для файла. */
    public static ContactData of(Contact[] contacts) {
        ContactRecord[] records = new ContactRecord[contacts.length];
        for (int i = 0; i < contacts.length; i++) {
            records[i] = ContactRecord.of(contacts[i]);
        }
        return new ContactData(CURRENT_VERSION, records);
    }

    /**
     * Восстанавливает массив контактов модели.
     *
     * @throws SerializationException если хотя бы одна запись повреждена
     */
    public Contact[] toContacts() throws SerializationException {
        if (contacts == null) {
            throw new SerializationException("В файле нет списка контактов");
        }
        if (schemaVersion != CURRENT_VERSION) {
            throw new SerializationException("Неподдерживаемая версия файла: " + schemaVersion);
        }
        Contact[] result = new Contact[contacts.length];
        for (int i = 0; i < contacts.length; i++) {
            result[i] = contacts[i].toContact();
        }
        return result;
    }
}
