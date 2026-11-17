package ru.litper.persistence.serialize;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.litper.model.Contact;
import ru.litper.model.CorporateContact;
import ru.litper.model.EmergencyContact;
import ru.litper.persistence.migration.ContactMigration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSON-формат справочника: roundtrip, чтение эталонного файла, битые данные.
 */
class JsonContactSerializerTest {

    private final Serializer<ContactData> serializer = new JsonContactSerializer();

    private static ContactData sample() {
        return ContactData.of(new Contact[]{
                new CorporateContact("Иван Иванов", "+79990000000", "ivan@example.com",
                        "ООО Ромашка", "Инженер", "123", "Главный контакт", "работа,важный"),
                new EmergencyContact("Пожарная служба", "101", "", "МЧС")
        });
    }

    @Test
    void reportsFormatAndExtension() {
        assertEquals("json", serializer.format());
        assertEquals("json", serializer.fileExtension());
    }

    @Test
    void saveThenLoadKeepsEveryField(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("out.json");
        ContactData original = sample();

        serializer.save(original, file);
        ContactData loaded = serializer.load(file);

        assertEquals(ContactData.CURRENT_VERSION, loaded.schemaVersion());
        assertArrayEquals(original.contacts(), loaded.contacts());
    }

    @Test
    void loadedContactsKeepTheirConcreteTypes(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("types.json");
        serializer.save(sample(), file);

        Contact[] contacts = serializer.load(file).toContacts();

        assertInstanceOf(CorporateContact.class, contacts[0]);
        assertInstanceOf(EmergencyContact.class, contacts[1]);
    }

    @Test
    void emptyBookRoundTrips(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("empty.json");

        serializer.save(new ContactData(ContactData.CURRENT_VERSION, new ContactRecord[0]), file);
        ContactData loaded = serializer.load(file);

        assertEquals(0, loaded.contacts().length);
    }

    @Test
    void loadReadsReferenceFixtureFromClasspath() throws Exception {
        try (var in = getClass().getResourceAsStream("/contacts.json")) {
            Path file = writeToTemp(in, "json");

            Contact[] contacts = serializer.load(file).toContacts();

            assertEquals(2, contacts.length);
            assertInstanceOf(CorporateContact.class, contacts[0]);
            assertEquals("Иван Иванов", contacts[0].getName());
            assertInstanceOf(EmergencyContact.class, contacts[1]);
            assertEquals("101", contacts[1].getPhone());
        }
    }

    @Test
    void loadMissingFileFails(@TempDir Path dir) {
        Path missing = dir.resolve("нет-такого.json");

        assertThrows(IOException.class, () -> serializer.load(missing));
    }

    @Test
    void loadTruncatedJsonFails() throws Exception {
        try (var in = getClass().getResourceAsStream("/contacts-broken.json")) {
            Path file = writeToTemp(in, "json");

            SerializationException error =
                    assertThrows(SerializationException.class, () -> serializer.load(file));

            assertEquals("Файл не является корректным JSON справочника", error.getMessage());
        }
    }

    @Test
    void loadFutureSchemaVersionIsRejected() throws Exception {
        try (var in = getClass().getResourceAsStream("/contacts-future-version.json")) {
            Path file = writeToTemp(in, "json");

            SerializationException error = assertThrows(SerializationException.class,
                    () -> serializer.load(file).toContacts());

            assertEquals("Неподдерживаемая версия файла: 99", error.getMessage());
        }
    }

    @Test
    void loadRejectsRecordWithUnknownType(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("bad-type.json"),
                """
                {
                  "schema_version" : 3,
                  "contacts" : [ {
                    "type" : "boss",
                    "name" : "Вася",
                    "phone" : "+7",
                    "email" : "",
                    "organization" : "",
                    "position" : "",
                    "internalNumber" : "",
                    "note" : "",
                    "tags" : ""
                  } ]
                }
                """, StandardCharsets.UTF_8);

        SerializationException error = assertThrows(SerializationException.class,
                () -> serializer.load(dir.resolve("bad-type.json")).toContacts());

        assertEquals("Неизвестный тип контакта: boss", error.getMessage());
    }

    @Test
    void saveWritesSnakeCaseVersionField(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("version.json");

        serializer.save(sample(), file);

        String json = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"schema_version\""));
        assertFalse(json.contains("\"schemaVersion\""));
    }

    @Test
    void loadsV1FixtureWithoutNewFields() throws Exception {
        Path file = writeToTemp(getClass().getResourceAsStream("/contacts-v1.json"), "json");

        ContactData loaded = serializer.load(file);
        Contact[] contacts = new ContactMigration().migrate(loaded).toContacts();

        assertEquals(1, loaded.schemaVersion());
        assertNull(loaded.contacts()[0].note());
        assertNull(loaded.contacts()[0].tags());
        assertEquals("", contacts[0].getNote());
        assertEquals("", contacts[0].getTags());
    }

    @Test
    void loadsV2FixtureAndKeepsNote() throws Exception {
        Path file = writeToTemp(getClass().getResourceAsStream("/contacts-v2.json"), "json");

        ContactData loaded = serializer.load(file);
        Contact[] contacts = new ContactMigration().migrate(loaded).toContacts();

        assertEquals(2, loaded.schemaVersion());
        assertEquals("Главный контакт", loaded.contacts()[0].note());
        assertNull(loaded.contacts()[0].tags());
        assertEquals("Главный контакт", contacts[0].getNote());
        assertEquals("", contacts[0].getTags());
    }

    @Test
    void loadsLegacyCamelCaseVersion(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("legacy.json");
        Files.writeString(file, """
                {
                  "schemaVersion" : 1,
                  "contacts" : [ {
                    "type" : "emergency",
                    "name" : "Пожарная",
                    "phone" : "101"
                  } ]
                }
                """, StandardCharsets.UTF_8);

        ContactData loaded = serializer.load(file);

        assertEquals(1, loaded.schemaVersion());
        assertEquals("Пожарная", loaded.contacts()[0].name());
    }

    @Test
    void downgradeToV1DoesNotWriteNewFields(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("v1.json");
        ContactData data = sample().forSchemaVersion(SchemaVersion.V1);

        serializer.save(data, file);

        String json = Files.readString(file, StandardCharsets.UTF_8);
        assertFalse(json.contains("\"note\""));
        assertFalse(json.contains("\"tags\""));
        assertEquals(1, serializer.load(file).schemaVersion());
    }

    private static Path writeToTemp(java.io.InputStream in, String suffix) throws IOException {
        Path file = Files.createTempFile("data", "." + suffix);
        Files.copy(in, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return file;
    }
}
