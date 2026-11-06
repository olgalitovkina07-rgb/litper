package ru.litper.persistence.serialize;

import org.junit.jupiter.api.Test;
import ru.litper.model.Contact;
import ru.litper.model.CorporateContact;
import ru.litper.model.EmergencyContact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Конвертация модели в записи файла и обратно.
 */
class ContactDataTest {

    private static Contact corporate() {
        return new CorporateContact("Иван Иванов", "+79990000000", "ivan@example.com",
                "ООО Ромашка", "Инженер", "123");
    }

    @Test
    void corporateKeepsTypeAndAllFields() {
        ContactRecord record = ContactRecord.of(corporate());

        assertEquals("corporate", record.type());
        assertEquals("Иван Иванов", record.name());
        assertEquals("+79990000000", record.phone());
        assertEquals("ivan@example.com", record.email());
        assertEquals("ООО Ромашка", record.organization());
        assertEquals("Инженер", record.position());
        assertEquals("123", record.internalNumber());
    }

    @Test
    void emergencyKeepsTypeAndLeavesExtraFieldsEmpty() {
        ContactRecord record = ContactRecord.of(new EmergencyContact("Пожарная служба", "101", "", "МЧС"));

        assertEquals("emergency", record.type());
        assertEquals("", record.position());
        assertEquals("", record.internalNumber());
    }

    @Test
    void recordRestoresCorporateContact() throws SerializationException {
        Contact restored = ContactRecord.of(corporate()).toContact();

        CorporateContact corporate = assertInstanceOf(CorporateContact.class, restored);
        assertEquals("Иван Иванов", corporate.getName());
        assertEquals("Инженер", corporate.getPosition());
        assertEquals("123", corporate.getInternalNumber());
    }

    @Test
    void recordRestoresEmergencyContact() throws SerializationException {
        Contact restored = ContactRecord.of(new EmergencyContact("Скорая помощь", "103", "", "МЧС")).toContact();

        EmergencyContact emergency = assertInstanceOf(EmergencyContact.class, restored);
        assertEquals("Скорая помощь", emergency.getName());
        assertEquals("103", emergency.getPhone());
    }

    @Test
    void recordsWithUnknownTypeAreRejected() {
        ContactRecord record = new ContactRecord("boss", "Вася", "+7", "", "", "", "");

        SerializationException error = assertThrows(SerializationException.class, record::toContact);

        assertEquals("Неизвестный тип контакта: boss", error.getMessage());
    }

    @Test
    void recordsWithEmptyRequiredFieldsAreRejected() {
        ContactRecord record = new ContactRecord("corporate", "", "+7", "", "", "", "");

        SerializationException error = assertThrows(SerializationException.class, record::toContact);

        assertEquals("У контакта не заполнены имя или телефон", error.getMessage());
    }

    @Test
    void nullFieldsBecomeEmptyStrings() throws SerializationException {
        ContactRecord record = new ContactRecord("emergency", "Пожарная", "101", null, null, null, null);

        Contact contact = record.toContact();

        assertEquals("", contact.getEmail());
        assertEquals("", contact.getOrganization());
    }

    @Test
    void corporateNoteAndTagsAreStored() {
        Contact contact = new CorporateContact("Иван", "+7", "", "", "Инженер", "123",
                "Важный клиент", "работа,важный");

        ContactRecord record = ContactRecord.of(contact);

        assertEquals("Важный клиент", record.note());
        assertEquals("работа,важный", record.tags());
    }

    @Test
    void recordRestoresNoteAndTags() throws SerializationException {
        ContactRecord record = new ContactRecord("corporate", "Иван", "+7",
                "", "", "Инженер", "123", "заметка", "тег");

        Contact contact = record.toContact();

        assertEquals("заметка", contact.getNote());
        assertEquals("тег", contact.getTags());
    }

    @Test
    void exportToV1ClearsNewFields() {
        ContactData current = ContactData.of(new Contact[]{
                new CorporateContact("Иван", "+7", "", "", "Инженер", "123", "заметка", "тег")
        });

        ContactData v1 = current.forSchemaVersion(SchemaVersion.V1);

        assertEquals(1, v1.schemaVersion());
        assertNull(v1.contacts()[0].note());
        assertNull(v1.contacts()[0].tags());
    }

    @Test
    void exportToV2KeepsNoteButClearsTags() {
        ContactData current = ContactData.of(new Contact[]{
                new CorporateContact("Иван", "+7", "", "", "Инженер", "123", "заметка", "тег")
        });

        ContactData v2 = current.forSchemaVersion(SchemaVersion.V2);

        assertEquals(2, v2.schemaVersion());
        assertEquals("заметка", v2.contacts()[0].note());
        assertNull(v2.contacts()[0].tags());
    }

    @Test
    void dataCarriesCurrentVersion() {
        ContactData data = ContactData.of(new Contact[]{corporate()});

        assertEquals(ContactData.CURRENT_VERSION, data.schemaVersion());
        assertEquals(1, data.contacts().length);
    }

    @Test
    void unknownVersionIsRejectedOnConversion() {
        ContactData data = new ContactData(999, new ContactRecord[0]);

        SerializationException error = assertThrows(SerializationException.class, data::toContacts);

        assertEquals("Неподдерживаемая версия файла: 999", error.getMessage());
    }

    @Test
    void missingContactListIsRejected() {
        ContactData data = new ContactData(ContactData.CURRENT_VERSION, null);

        SerializationException error = assertThrows(SerializationException.class, data::toContacts);

        assertEquals("В файле нет списка контактов", error.getMessage());
    }
}
