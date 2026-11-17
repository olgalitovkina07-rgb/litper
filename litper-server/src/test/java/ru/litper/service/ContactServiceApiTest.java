package ru.litper.service;

import org.junit.jupiter.api.Test;
import ru.litper.model.Contact;
import ru.litper.model.CorporateContact;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ID контактов, слушатели изменений и конфликты для REST-слоя.
 */
class ContactServiceApiTest {

    private final ContactService service = new ContactService();

    private static CorporateContact corporate(String name, String phone) {
        return new CorporateContact(name, phone, "e@x.ru", "ООО", "Инженер", "123");
    }

    @Test
    void addReturnsIdThatFindsContact() {
        CorporateContact contact = corporate("Иван", "+79990000000");

        long id = service.add(contact);

        assertTrue(id > 0);
        assertEquals(contact, service.getById(id));
    }

    @Test
    void updateKeepsIdAfterNameChange() {
        CorporateContact original = corporate("Иван", "+79990000000");
        long id = service.add(original);

        CorporateContact renamed = corporate("Пётр", "+79990000001");
        assertTrue(service.update(original, renamed));

        assertEquals(renamed, service.getById(id));
        assertEquals(id, service.idOf(renamed));
    }

    @Test
    void removeByPrefixRemovesIdsOfDeletedContacts() {
        CorporateContact first = corporate("Иван Иванов", "+79990000000");
        CorporateContact second = corporate("Иван Петров", "+79990000001");
        CorporateContact other = corporate("Пётр Сидоров", "+79990000002");
        long firstId = service.add(first);
        long secondId = service.add(second);
        long otherId = service.add(other);

        assertEquals(2, service.removeByPrefix("Иван"));

        assertNull(service.getById(firstId));
        assertNull(service.getById(secondId));
        assertNotNull(service.getById(otherId));
    }

    @Test
    void entriesContainStableIds() {
        long id = service.add(corporate("Анна", "+79990000000"));

        ContactService.ContactEntry[] entries = service.entries();

        assertEquals(1, entries.length);
        assertEquals(id, entries[0].id());
        assertEquals("Анна", entries[0].contact().getName());
    }

    @Test
    void listenersAreCalledAfterChanges() {
        AtomicInteger calls = new AtomicInteger();
        service.addChangeListener(calls::incrementAndGet);
        CorporateContact contact = corporate("Иван", "+79990000000");

        service.add(contact);
        service.update(contact, corporate("Пётр", "+79990000001"));
        service.clear();

        assertEquals(3, calls.get());
    }

    @Test
    void duplicateNameAndPhoneIsRejected() {
        service.addUnique(corporate("Иван", "+79990000000"));

        assertThrows(DuplicateContactException.class,
                () -> service.addUnique(corporate("ИВАН", " +79990000000 ")));
    }

    @Test
    void duplicateNameWithDifferentPhoneIsAllowed() {
        long first = service.addUnique(corporate("Иван", "+79990000000"));
        long second = service.addUnique(corporate("Иван", "+79990000001"));

        assertTrue(first != second);
        assertEquals(2, service.size());
    }

    @Test
    void entriesSnapshotIsIndependentOfLaterChanges() {
        service.add(corporate("Иван", "+79990000000"));

        Contact[] snapshot = service.getAll();
        service.add(corporate("Пётр", "+79990000001"));

        assertEquals(1, snapshot.length);
    }
}
