package ru.litper.persistence.migration;

import ru.litper.model.ContactType;
import ru.litper.persistence.serialize.ContactData;
import ru.litper.persistence.serialize.ContactRecord;
import ru.litper.persistence.serialize.SchemaVersion;
import ru.litper.persistence.serialize.SerializationException;

/**
 * Поэтапная миграция справочника к текущей версии схемы.
 *
 * <p>Каждый шаг добавляет одно новое поле и заполняет его значением по умолчанию,
 * поэтому старые файлы открываются без ошибок: v1 → v2 добавляет заметку,
 * v2 → v3 — теги. Шаги применяются по порядку, пока версия не станет текущей.</p>
 */
public final class ContactMigration {

    private static final String DEFAULT_NOTE = "";
    private static final String DEFAULT_TAGS = "";

    /**
     * Мигрирует данные к {@link SchemaVersion#CURRENT}.
     *
     * @throws SerializationException если версия неподдерживаемая или запись повреждена
     */
    public ContactData migrate(ContactData data) throws SerializationException {
        if (data.contacts() == null) {
            throw new SerializationException("В файле нет списка контактов");
        }

        int version = data.schemaVersion();
        if (!ContactData.isSupportedVersion(version)) {
            throw new SerializationException("Неподдерживаемая версия файла: " + version);
        }

        ContactRecord[] records = data.contacts();
        if (version < SchemaVersion.V2.number()) {
            records = addNote(records);
        }
        if (version < SchemaVersion.V3.number()) {
            records = addTags(records);
        }

        validate(records);
        return new ContactData(ContactData.CURRENT_VERSION, records);
    }

    /** Что именно будет добавлено при миграции с указанной версии. */
    public static String describe(int fromVersion) {
        StringBuilder text = new StringBuilder();
        if (fromVersion < SchemaVersion.V2.number()) {
            text.append("1 → 2: добавить поле «заметка» (пустое значение по умолчанию)");
        }
        if (fromVersion < SchemaVersion.V3.number()) {
            if (!text.isEmpty()) {
                text.append(System.lineSeparator());
            }
            text.append("2 → 3: добавить поле «теги» (пустое значение по умолчанию)");
        }
        return text.toString();
    }

    private static ContactRecord[] addNote(ContactRecord[] records) {
        ContactRecord[] result = new ContactRecord[records.length];
        for (int i = 0; i < records.length; i++) {
            ContactRecord record = records[i];
            result[i] = new ContactRecord(
                    record.type(),
                    record.name(),
                    record.phone(),
                    record.email(),
                    record.organization(),
                    record.position(),
                    record.internalNumber(),
                    valueOrDefault(record.note(), DEFAULT_NOTE),
                    record.tags());
        }
        return result;
    }

    private static ContactRecord[] addTags(ContactRecord[] records) {
        ContactRecord[] result = new ContactRecord[records.length];
        for (int i = 0; i < records.length; i++) {
            ContactRecord record = records[i];
            result[i] = new ContactRecord(
                    record.type(),
                    record.name(),
                    record.phone(),
                    record.email(),
                    record.organization(),
                    record.position(),
                    record.internalNumber(),
                    record.note(),
                    valueOrDefault(record.tags(), DEFAULT_TAGS));
        }
        return result;
    }

    private static void validate(ContactRecord[] records) throws SerializationException {
        for (int i = 0; i < records.length; i++) {
            ContactRecord record = records[i];
            int number = i + 1;
            if (isBlank(record.type()) || isBlank(record.name()) || isBlank(record.phone())) {
                throw new SerializationException(
                        "Запись " + number + ": не заполнены обязательные поля");
            }
            try {
                ContactType.fromCsvId(record.type().trim());
            } catch (IllegalArgumentException e) {
                throw new SerializationException(
                        "Запись " + number + ": неизвестный тип контакта: " + record.type());
            }
        }
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
