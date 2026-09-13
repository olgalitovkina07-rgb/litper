package ru.litper.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.litper.model.Contact;
import ru.litper.model.CorporateContact;
import ru.litper.model.EmergencyContact;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CsvLoaderTest {

    private static final String HEADER =
            "type;name;phone;email;organization;position;internalNumber";

    private final CsvLoader loader = new CsvLoader();

    @Test
    void parsesCorporateLine() throws Exception {
        Contact contact = loader.parseLine(
                "corporate;Иван Иванов;+79990000000;ivan@example.com;ООО Ромашка;Инженер;123", 2);

        CorporateContact corporate = assertInstanceOf(CorporateContact.class, contact);
        assertEquals("Иван Иванов", corporate.getName());
        assertEquals("+79990000000", corporate.getPhone());
        assertEquals("Инженер", corporate.getPosition());
        assertEquals("123", corporate.getInternalNumber());
    }

    @Test
    void parsesEmergencyLine() throws Exception {
        Contact contact = loader.parseLine(
                "emergency;Пожарная служба;101;;МЧС;;", 2);

        EmergencyContact emergency = assertInstanceOf(EmergencyContact.class, contact);
        assertEquals("Пожарная служба", emergency.getName());
        assertEquals("101", emergency.getPhone());
    }

    @Test
    void wrongFieldCountIsSkippableError() {
        CsvParseException error = assertThrows(CsvParseException.class,
                () -> loader.parseLine("corporate;Строка;без;достаточного;количества", 3));

        assertEquals(CsvErrorCode.WRONG_FIELD_COUNT, error.getCode());
    }

    @Test
    void unknownTypeIsSkippableError() {
        CsvParseException error = assertThrows(CsvParseException.class,
                () -> loader.parseLine("boss;Вася;+7;;;Дир;11", 4));

        assertEquals(CsvErrorCode.UNKNOWN_TYPE, error.getCode());
    }

    @Test
    void nonDigitInternalNumberIsError() {
        CsvParseException error = assertThrows(CsvParseException.class,
                () -> loader.parseLine("corporate;Вася;+7;;;Дир;абв", 5));

        assertEquals(CsvErrorCode.BAD_NUMBER, error.getCode());
    }

    @Test
    void loadSkipsBrokenLinesButKeepsValidOnes(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("contacts.csv");
        Files.write(file, List.of(
                HEADER,
                "corporate;Иван;+7;e;o;p;123",
                "corporate;Битая;+7;;;p;не-число",
                "неизвестный;a;b;c;d;e;f",
                "emergency;Пожарная;101;;МЧС;;"
        ), StandardCharsets.UTF_8);

        CsvLoadResult result = loader.load(file);

        assertEquals(2, result.contacts().size());
        assertEquals(2, result.skippedLines());
        assertInstanceOf(CorporateContact.class, result.contacts().get(0));
        assertInstanceOf(EmergencyContact.class, result.contacts().get(1));
    }

    @Test
    void emptyFileLoadsNothing(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("empty.csv");
        Files.createFile(file);

        CsvLoadResult result = loader.load(file);

        assertEquals(0, result.contacts().size());
        assertEquals(0, result.skippedLines());
    }
}