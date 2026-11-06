package ru.litper.persistence.serialize;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import ru.litper.model.Contact;

/**
 * Содержимое файла справочника: версия схемы и список контактов.
 *
 * <p>Версия записывается в заголовок каждого формата. При чтении старых файлов
 * по ней запускается цепочка миграций, а при экспорте можно выбрать старую
 * версию схемы.</p>
 */
public record ContactData(
        @JsonProperty("schema_version") @JsonAlias("schemaVersion") int schemaVersion,
        ContactRecord[] contacts) {

    public static final int MIN_VERSION = SchemaVersion.MIN.number();

    /** Текущая версия схемы файла. */
    public static final int CURRENT_VERSION = SchemaVersion.CURRENT.number();

    /** Снимок контактов сервиса в виде данных для файла. */
    public static ContactData of(Contact[] contacts) {
        ContactRecord[] records = new ContactRecord[contacts.length];
        for (int i = 0; i < contacts.length; i++) {
            records[i] = ContactRecord.of(contacts[i]);
        }
        return new ContactData(CURRENT_VERSION, records);
    }

    public static boolean isSupportedVersion(int version) {
        return version >= MIN_VERSION && version <= CURRENT_VERSION;
    }

    /**
     * Готовит данные для сохранения в выбранной версии схемы.
     *
     * <p>Поля, которых в этой версии ещё не было, очищаются — это осознанная
     * потеря данных при обратной совместимости.</p>
     */
    public ContactData forSchemaVersion(SchemaVersion target) {
        ContactRecord[] result = new ContactRecord[contacts.length];
        for (int i = 0; i < contacts.length; i++) {
            ContactRecord record = contacts[i];
            result[i] = new ContactRecord(
                    record.type(),
                    record.name(),
                    record.phone(),
                    record.email(),
                    record.organization(),
                    record.position(),
                    record.internalNumber(),
                    target.hasNote() ? record.note() : null,
                    target.hasTags() ? record.tags() : null);
        }
        return new ContactData(target.number(), result);
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
