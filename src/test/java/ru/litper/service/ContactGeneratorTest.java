package ru.litper.service;

import org.junit.jupiter.api.Test;
import ru.litper.model.Contact;
import ru.litper.model.CorporateContact;
import ru.litper.model.EmergencyContact;

import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Генерация данных для дерева: объём, детерминированность, отмена.
 */
class ContactGeneratorTest {

    private final ContactGenerator generator = new ContactGenerator();

    @Test
    void generatesRequestedNumberOfContacts() {
        Contact[] contacts = generator.generate(500, 1L, null);

        assertEquals(500, contacts.length);
    }

    @Test
    void generatesOnlyEditableContacts() {
        for (Contact contact : generator.generate(50, 1L, null)) {
            assertInstanceOf(CorporateContact.class, contact);
        }
    }

    @Test
    void sameSeedProducesSameNames() {
        Contact[] first = generator.generate(20, 7L, null);
        Contact[] second = generator.generate(20, 7L, null);

        assertEquals(first[0].getName(), second[0].getName());
    }

    @Test
    void differentSeedProducesDifferentNames() {
        Contact[] first = generator.generate(20, 1L, null);
        Contact[] second = generator.generate(20, 2L, null);

        assertNotEquals(first[0].getName() + first[1].getName(),
                second[0].getName() + second[1].getName());
    }

    @Test
    void generatedContactsAreValid() {
        Contact contact = generator.generate(1, 1L, null)[0];

        assertTrue(((CorporateContact) contact).validate().isEmpty());
    }

    @Test
    void reportsProgressUpToTotal() {
        int[] lastReported = {0};

        generator.generate(1_000, 1L, (done, total) -> {
            lastReported[0] = done;
            return true;
        });

        assertEquals(1_000, lastReported[0]);
    }

    @Test
    void throwsCancellationExceptionWhenProgressAsksToStop() {
        assertThrows(CancellationException.class,
                () -> generator.generate(2_000, 1L, (done, total) -> false));
    }

    @Test
    void returnsEmptyArrayForNonPositiveCount() {
        assertEquals(0, generator.generate(0, 1L, null).length);
    }

    @Test
    void generatedNamesDifferAcrossContacts() {
        Contact[] contacts = generator.generate(100, 3L, null);

        assertTrue(contacts[0].getName() != null && !contacts[0].getName().isEmpty());
        assertFalse(contacts[0].getName().equals(contacts[1].getName())
                && contacts[1].getName().equals(contacts[2].getName()));
    }

    @Test
    void emergencyContactIsNotCreatedByGenerator() {
        for (Contact contact : generator.generate(30, 5L, null)) {
            assertFalse(contact instanceof EmergencyContact);
        }
    }
}