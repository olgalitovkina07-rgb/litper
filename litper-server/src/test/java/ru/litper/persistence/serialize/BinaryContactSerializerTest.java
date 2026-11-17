package ru.litper.persistence.serialize;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.litper.model.Contact;
import ru.litper.model.CorporateContact;
import ru.litper.model.EmergencyContact;
import ru.litper.persistence.migration.ContactMigration;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Бинарный формат на java.io.Serializable: roundtrip, версии и защита от чужих файлов.
 */
class BinaryContactSerializerTest {

    private final Serializer<ContactData> serializer = new BinaryContactSerializer();

    private static ContactData sample() {
        return ContactData.of(new Contact[]{
                new CorporateContact("Иван Иванов", "+79990000000", "ivan@example.com",
                        "ООО Ромашка", "Инженер", "123", "Главный контакт", "работа,важный"),
                new EmergencyContact("Пожарная служба", "101", "", "МЧС")
        });
    }

    @Test
    void reportsFormatAndExtension() {
        assertEquals("binary", serializer.format());
        assertEquals("bin", serializer.fileExtension());
    }

    @Test
    void saveThenLoadKeepsEveryField(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("out.bin");
        ContactData original = sample();

        serializer.save(original, file);
        ContactData loaded = serializer.load(file);

        assertEquals(ContactData.CURRENT_VERSION, loaded.schemaVersion());
        assertArrayEquals(original.contacts(), loaded.contacts());
    }

    @Test
    void loadedContactsKeepTheirConcreteTypes(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("types.bin");
        serializer.save(sample(), file);

        Contact[] contacts = serializer.load(file).toContacts();

        assertInstanceOf(CorporateContact.class, contacts[0]);
        assertInstanceOf(EmergencyContact.class, contacts[1]);
    }

    @Test
    void emptyBookRoundTrips(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("empty.bin");

        serializer.save(new ContactData(ContactData.CURRENT_VERSION, new ContactRecord[0]), file);
        ContactData loaded = serializer.load(file);

        assertEquals(0, loaded.contacts().length);
    }

    @Test
    void fileIsRealBinaryAndStartsWithJavaSerializationMagic(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("magic.bin");

        serializer.save(sample(), file);
        byte[] bytes = Files.readAllBytes(file);

        assertEquals((byte) 0xAC, bytes[0]);
        assertEquals((byte) 0xED, bytes[1]);
    }

    @Test
    void loadMissingFileFails(@TempDir Path dir) {
        Path missing = dir.resolve("нет-такого.bin");

        assertThrows(IOException.class, () -> serializer.load(missing));
    }

    @Test
    void loadRejectsForeignFileWithoutStreamHeader(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("foreign.bin");
        Files.write(file, "это не справочник".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        SerializationException error =
                assertThrows(SerializationException.class, () -> serializer.load(file));

        assertEquals("Файл не является справочником в бинарном формате", error.getMessage());
    }

    @Test
    void loadRejectsSerializedObjectOfAnotherType(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("other-object.bin");
        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(file))) {
            out.writeObject("это не справочник");
        }

        SerializationException error =
                assertThrows(SerializationException.class, () -> serializer.load(file));

        assertEquals("Файл не является справочником в бинарном формате", error.getMessage());
    }

    @Test
    void loadRejectsTruncatedFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("truncated.bin");
        serializer.save(sample(), file);
        byte[] all = Files.readAllBytes(file);
        Files.write(file, Arrays.copyOf(all, all.length / 2));

        assertThrows(SerializationException.class, () -> serializer.load(file));
    }

    @Test
    void loadRejectsUnsupportedVersion(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("wrong-version.bin");
        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(file))) {
            out.writeObject(new ContactBookFile(99, sample().contacts()));
        }

        SerializationException error =
                assertThrows(SerializationException.class, () -> serializer.load(file));

        assertEquals("Неподдерживаемая версия файла: 99", error.getMessage());
    }

    @Test
    void loadRejectsFileWithoutContactList(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("no-contacts.bin");
        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(file))) {
            out.writeObject(new ContactBookFile(ContactData.CURRENT_VERSION, null));
        }

        SerializationException error =
                assertThrows(SerializationException.class, () -> serializer.load(file));

        assertEquals("Повреждён бинарный файл: нет списка контактов", error.getMessage());
    }

    @Test
    void loadsVersion1FileWithoutNewFields(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("v1.bin");

        serializer.save(sample().forSchemaVersion(SchemaVersion.V1), file);
        ContactData loaded = serializer.load(file);
        ContactData migrated = new ContactMigration().migrate(loaded);

        assertEquals(1, loaded.schemaVersion());
        assertNull(loaded.contacts()[0].note());
        assertNull(loaded.contacts()[0].tags());
        assertEquals("", migrated.contacts()[0].note());
        assertEquals("", migrated.contacts()[0].tags());
    }

    @Test
    void savesV1WithoutNewFields(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("v1-saved.bin");

        serializer.save(sample().forSchemaVersion(SchemaVersion.V1), file);
        ContactData loaded = serializer.load(file);

        assertEquals(1, loaded.schemaVersion());
        assertEquals("Иван Иванов", loaded.contacts()[0].name());
        assertNull(loaded.contacts()[0].note());
        assertNull(loaded.contacts()[0].tags());
    }

    @Test
    void savesV2WithNoteOnly(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("v2-saved.bin");
        ContactRecord record = new ContactRecord("corporate", "Иван", "+7",
                "", "", "Инженер", "123", "заметка", "тег");
        ContactData v2 = new ContactData(2, new ContactRecord[]{record});

        serializer.save(v2, file);
        ContactData loaded = serializer.load(file);

        assertEquals(2, loaded.schemaVersion());
        assertEquals("заметка", loaded.contacts()[0].note());
        assertNull(loaded.contacts()[0].tags());
    }
}
