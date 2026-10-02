package ru.litper.service;

import org.junit.jupiter.api.Test;
import ru.litper.model.Contact;
import ru.litper.model.CorporateContact;
import ru.litper.model.EmergencyContact;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Сервис контактов поверх префиксного дерева: поиск без учёта регистра,
 * автодополнение, переиндексация при смене имени.
 */
class ContactServiceTest {

    private final ContactService service = new ContactService();

    private static CorporateContact corporate(String name) {
        return new CorporateContact(name, "+79990000000", "e@x.ru", "ООО", "Инженер", "123");
    }

    @Test
    void addStoresContactInTree() {
        service.add(corporate("Иван"));

        assertEquals(1, service.size());
    }

    @Test
    void findByNameIgnoresCase() {
        service.add(corporate("Иванов Иван"));

        assertEquals(1, service.findByName("ИВАНОВ ИВАН").length);
    }

    @Test
    void suggestReturnsContactsByPrefix() {
        service.add(corporate("Иван Иванов"));
        service.add(corporate("Иван Петров"));
        service.add(corporate("Пётр Сидоров"));

        assertEquals(2, service.suggest("иван", 10).length);
    }

    @Test
    void suggestRespectsLimit() {
        service.add(corporate("Иван Иванов"));
        service.add(corporate("Иван Петров"));

        assertEquals(1, service.suggest("иван", 1).length);
    }

    @Test
    void countByPrefixCountsSubtree() {
        service.add(corporate("Иван Иванов"));
        service.add(corporate("Иван Петров"));
        service.add(corporate("Пётр Сидоров"));

        assertEquals(2, service.countByPrefix("иван"));
    }

    @Test
    void updateReindexesContactWhenNameChanges() {
        CorporateContact original = corporate("Иван Иванов");
        service.add(original);

        CorporateContact renamed = corporate("Пётр Иванов");
        boolean updated = service.update(original, renamed);

        assertTrue(updated);
        assertEquals(0, service.findByName("Иван Иванов").length);
        assertEquals(1, service.findByName("Пётр Иванов").length);
    }

    @Test
    void updateReturnsFalseForUnknownContact() {
        assertFalse(service.update(corporate("Не в дереве"), corporate("Другой")));
    }

    @Test
    void removeDeletesContactFromTree() {
        CorporateContact contact = corporate("Иван Иванов");
        service.add(contact);

        service.remove(contact);

        assertEquals(0, service.size());
    }

    @Test
    void replaceAllSwapsContent() {
        service.add(corporate("Иван Иванов"));

        service.replaceAll(new Contact[]{corporate("Пётр Сидоров")});

        assertEquals(1, service.size());
        assertEquals(0, service.findByName("Иван Иванов").length);
    }

    @Test
    void getAllReturnsAlphabeticalSnapshot() {
        service.add(corporate("Пётр"));
        service.add(corporate("Анна"));

        Contact[] all = service.getAll();

        assertEquals("Анна", all[0].getName());
        assertEquals("Пётр", all[1].getName());
    }

    @Test
    void removeByPrefixDeletesSubtree() {
        service.add(corporate("Иван Иванов"));
        service.add(corporate("Иван Петров"));
        service.add(corporate("Пётр Сидоров"));

        int removed = service.removeByPrefix("Иван");

        assertEquals(2, removed);
        assertEquals(1, service.size());
    }

    @Test
    void removeByEmptyPrefixIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.removeByPrefix("  "));
    }

    @Test
    void treeKeepsReadOnlyContactLoadedFromFile() {
        service.add(new EmergencyContact("Пожарная служба", "101", "", "МЧС"));

        assertEquals(1, service.findByName("Пожарная служба").length);
    }

    @Test
    void generatedContactsAreSearchable() {
        Contact[] generated = new ContactGenerator().generate(200, 11L, null);
        service.addAll(generated);

        assertEquals(200, service.size());
        assertEquals(200, service.countByPrefix(""));
    }

    @Test
    void snapshotIsIndependentOfLaterChanges() {
        service.add(corporate("Иван"));
        Contact[] snapshot = service.getAll();

        service.add(corporate("Пётр"));

        assertEquals(1, snapshot.length);
    }

    @Test
    void addAllKeepsDuplicateNamesAsSeparateContacts() {
        Contact[] contacts = {corporate("Иван"), corporate("Иван")};

        service.addAll(contacts);

        assertArrayEquals(new String[]{"Иван", "Иван"},
                new String[]{service.findByName("Иван")[0].getName(),
                        service.findByName("Иван")[1].getName()});
    }
}