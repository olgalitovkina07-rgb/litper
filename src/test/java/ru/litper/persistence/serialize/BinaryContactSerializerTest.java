package ru.litper.persistence.serialize;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.litper.model.Contact;
import ru.litper.model.CorporateContact;
import ru.litper.model.EmergencyContact;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Собственный бинарный формат: roundtrip и защита от файлов не того формата.
 */
class BinaryContactSerializerTest {

    private final Serializer<ContactData> serializer = new BinaryContactSerializer();

    private static ContactData sample() {
        return ContactData.of(new Contact[]{
                new CorporateContact("Иван Иванов", "+79990000000", "ivan@example.com",
                        "ООО Ромашка", "Инженер", "123"),
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
    void loadMissingFileFails(@TempDir Path dir) {
        Path missing = dir.resolve("нет-такого.bin");

        assertThrows(IOException.class, () -> serializer.load(missing));
    }

    @Test
    void loadRejectsForeignFileWithoutMagic(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("foreign.bin");
        Files.write(file, "это не справочник".getBytes(java.nio.charset.StandardCharsets.UTF_8));

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
        serializer.save(sample(), file);

        byte[] all = Files.readAllBytes(file);
        // первые 4 байта — метка формата, следующие 4 — версия схемы
        ByteBuffer.wrap(all).putInt(4, 99);
        Files.write(file, all);

        SerializationException error =
                assertThrows(SerializationException.class, () -> serializer.load(file));

        assertEquals("Неподдерживаемая версия файла: 99", error.getMessage());
    }

    @Test
    void loadRejectsNegativeRecordCount(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("bad-count.bin");
        serializer.save(sample(), file);

        byte[] all = Files.readAllBytes(file);
        ByteBuffer.wrap(all).putInt(8, -5);
        Files.write(file, all);

        SerializationException error =
                assertThrows(SerializationException.class, () -> serializer.load(file));

        assertEquals("Повреждён заголовок файла: неверное число записей", error.getMessage());
    }
}
