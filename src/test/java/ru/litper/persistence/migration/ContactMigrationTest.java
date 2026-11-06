package ru.litper.persistence.migration;

import org.junit.jupiter.api.Test;
import ru.litper.persistence.serialize.ContactData;
import ru.litper.persistence.serialize.ContactRecord;
import ru.litper.persistence.serialize.SchemaVersion;
import ru.litper.persistence.serialize.SerializationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContactMigrationTest {

    private final ContactMigration migration = new ContactMigration();

    private static ContactRecord corporateWithoutNewFields() {
        return new ContactRecord("corporate", "Иван Иванов", "+79990000000",
                "ivan@example.com", "ООО Ромашка", "Инженер", "123");
    }

    @Test
    void v1GetsEmptyNoteAndTags() throws SerializationException {
        ContactData v1 = new ContactData(1, new ContactRecord[]{corporateWithoutNewFields()});

        ContactData current = migration.migrate(v1);

        assertEquals(ContactData.CURRENT_VERSION, current.schemaVersion());
        assertEquals("", current.contacts()[0].note());
        assertEquals("", current.contacts()[0].tags());
    }

    @Test
    void v1KeepsOriginalFields() throws SerializationException {
        ContactData v1 = new ContactData(1, new ContactRecord[]{corporateWithoutNewFields()});

        ContactRecord migrated = migration.migrate(v1).contacts()[0];

        assertEquals("Иван Иванов", migrated.name());
        assertEquals("Инженер", migrated.position());
        assertEquals("123", migrated.internalNumber());
    }

    @Test
    void v2KeepsNoteAndAddsTags() throws SerializationException {
        ContactRecord v2Record = new ContactRecord("corporate", "Иван", "+7",
                "", "", "Инженер", "123", "Важный клиент", null);
        ContactData v2 = new ContactData(2, new ContactRecord[]{v2Record});

        ContactRecord migrated = migration.migrate(v2).contacts()[0];

        assertEquals("Важный клиент", migrated.note());
        assertEquals("", migrated.tags());
    }

    @Test
    void currentVersionIsNotChanged() throws SerializationException {
        ContactRecord record = new ContactRecord("corporate", "Иван", "+7",
                "", "", "Инженер", "123", "заметка", "тег");
        ContactData current = new ContactData(ContactData.CURRENT_VERSION, new ContactRecord[]{record});

        ContactData result = migration.migrate(current);

        assertEquals(record, result.contacts()[0]);
    }

    @Test
    void futureVersionIsRejected() {
        ContactData future = new ContactData(ContactData.CURRENT_VERSION + 1, new ContactRecord[0]);

        SerializationException error = assertThrows(SerializationException.class,
                () -> migration.migrate(future));

        assertEquals("Неподдерживаемая версия файла: " + (ContactData.CURRENT_VERSION + 1),
                error.getMessage());
    }

    @Test
    void versionZeroIsRejected() {
        ContactData broken = new ContactData(0, new ContactRecord[0]);

        assertThrows(SerializationException.class, () -> migration.migrate(broken));
    }

    @Test
    void blankRequiredFieldIsReportedWithRecordNumber() {
        ContactRecord broken = new ContactRecord("corporate", "", "+7",
                "", "", "Инженер", "123");
        ContactData v1 = new ContactData(1, new ContactRecord[]{corporateWithoutNewFields(), broken});

        SerializationException error = assertThrows(SerializationException.class,
                () -> migration.migrate(v1));

        assertEquals("Запись 2: не заполнены обязательные поля", error.getMessage());
    }

    @Test
    void unknownTypeIsReportedWithRecordNumber() {
        ContactRecord broken = new ContactRecord("boss", "Вася", "+7",
                "", "", "", "");
        ContactData v1 = new ContactData(1, new ContactRecord[]{broken});

        SerializationException error = assertThrows(SerializationException.class,
                () -> migration.migrate(v1));

        assertEquals("Запись 1: неизвестный тип контакта: boss", error.getMessage());
    }

    @Test
    void missingContactsArrayIsRejected() {
        ContactData broken = new ContactData(1, null);

        SerializationException error = assertThrows(SerializationException.class,
                () -> migration.migrate(broken));

        assertEquals("В файле нет списка контактов", error.getMessage());
    }

    @Test
    void describeListsStepsFromV1() {
        String description = ContactMigration.describe(SchemaVersion.V1.number());

        assertTrue(description.contains("1 → 2"));
        assertTrue(description.contains("2 → 3"));
    }

    @Test
    void describeFromV2HasOnlySecondStep() {
        String description = ContactMigration.describe(SchemaVersion.V2.number());

        assertTrue(description.contains("2 → 3"));
        assertFalse(description.contains("1 → 2"));
    }
}
