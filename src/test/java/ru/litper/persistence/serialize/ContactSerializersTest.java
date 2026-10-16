package ru.litper.persistence.serialize;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Выбор формата по расширению — так GUI подбирает сериализатор для файла.
 */
class ContactSerializersTest {

    @Test
    void picksJsonForJsonFile() {
        Serializer<ContactData> serializer = ContactSerializers.forFile(Path.of("contacts.json"));

        assertInstanceOf(JsonContactSerializer.class, serializer);
    }

    @Test
    void picksBinaryForBinFile() {
        Serializer<ContactData> serializer = ContactSerializers.forFile(Path.of("data/contacts.BIN"));

        assertInstanceOf(BinaryContactSerializer.class, serializer);
    }

    @Test
    void picksJsonForUnknownExtension() {
        Serializer<ContactData> serializer = ContactSerializers.forFile(Path.of("contacts.txt"));

        assertInstanceOf(JsonContactSerializer.class, serializer);
    }
}
