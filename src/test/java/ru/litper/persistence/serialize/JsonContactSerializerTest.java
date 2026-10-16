package ru.litper.persistence.serialize;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.litper.model.Contact;
import ru.litper.model.CorporateContact;
import ru.litper.model.EmergencyContact;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * JSON-формат справочника: roundtrip, чтение эталонного файла, битые данные.
 */
class JsonContactSerializerTest {

    private final Serializer<ContactData> serializer = new JsonContactSerializer();

    private static ContactData sample() {
        return ContactData.of(new Contact[]{
                new CorporateContact("Иван Иванов", "+79990000000", "ivan@example.com",
                        "ООО Ромашка", "Инженер", "123"),
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
                  "schemaVersion" : 1,
                  "contacts" : [ {
                    "type" : "boss",
                    "name" : "Вася",
                    "phone" : "+7",
                    "email" : "",
                    "organization" : "",
                    "position" : "",
                    "internalNumber" : ""
                  } ]
                }
                """, StandardCharsets.UTF_8);

        SerializationException error = assertThrows(SerializationException.class,
                () -> serializer.load(dir.resolve("bad-type.json")).toContacts());

        assertEquals("Неизвестный тип контакта: boss", error.getMessage());
    }

    private static Path writeToTemp(java.io.InputStream in, String suffix) throws IOException {
        Path file = Files.createTempFile("data", "." + suffix);
        Files.copy(in, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return file;
    }
}
