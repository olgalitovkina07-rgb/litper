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
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvWriterTest {

    @Test
    void savedFileCanBeLoadedBack(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("roundtrip.csv");
        List<Contact> contacts = List.of(
                new CorporateContact("Иван Иванов", "+7999", "i@e.ru",
                        "ООО Ромашка", "Инженер", "123"),
                new EmergencyContact("Пожарная служба", "101", "", "МЧС"));

        new CsvWriter().write(file, contacts);
        CsvLoadResult result = new CsvLoader().load(file);

        assertEquals(2, result.contacts().size());
        assertEquals(0, result.skippedLines());
        assertEquals("Иван Иванов", result.contacts().get(0).getName());
        assertEquals("Пожарная служба", result.contacts().get(1).getName());
    }

    @Test
    void fileStartsWithHeader(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("out.csv");
        new CsvWriter().write(file, List.of());

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);

        assertTrue(lines.get(0).startsWith("type;name;phone;email;organization;position;internalNumber"));
    }
}