package ru.litper.persistence;

import ru.litper.model.Contact;
import ru.litper.model.ContactType;
import ru.litper.model.CorporateContact;
import ru.litper.model.EmergencyContact;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Сохраняет справочник контактов в CSV в том же формате,
 * который читает {@link CsvLoader}.
 */
public final class CsvWriter {

    private static final String HEADER = "type;name;phone;email;organization;position;internalNumber";

    public void write(Path file, List<Contact> contacts) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write(HEADER);
            writer.newLine();
            for (Contact contact : contacts) {
                writer.write(toLine(contact));
                writer.newLine();
            }
        }
    }

    private static String toLine(Contact contact) {
        if (contact instanceof CorporateContact corporate) {
            return join(
                    ContactType.CORPORATE.getCsvId(),
                    corporate.getName(),
                    corporate.getPhone(),
                    corporate.getEmail(),
                    corporate.getOrganization(),
                    corporate.getPosition(),
                    corporate.getInternalNumber());
        }
        if (contact instanceof EmergencyContact emergency) {
            return join(
                    ContactType.EMERGENCY.getCsvId(),
                    emergency.getName(),
                    emergency.getPhone(),
                    emergency.getEmail(),
                    emergency.getOrganization(),
                    "",
                    "");
        }
        throw new IllegalArgumentException("Неизвестный тип контакта: " + contact.getClass());
    }

    private static String join(String... values) {
        return String.join(";", values);
    }
}