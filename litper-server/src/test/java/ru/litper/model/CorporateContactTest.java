package ru.litper.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorporateContactTest {

    @Test
    void corporateContactImplementsEditable() {
        CorporateContact contact = validCorporateContact();

        assertTrue(contact instanceof Editable);
    }

    @Test
    void validContactHasNoErrors() {
        CorporateContact contact = validCorporateContact();

        assertEquals(List.of(), contact.validate());
    }

    @Test
    void emptyNameProducesError() {
        CorporateContact contact = new CorporateContact(
                "", "+7999", "i@e.ru", "ООО Р", "Инженер", "123");

        assertFalse(contact.validate().isEmpty());
        assertTrue(contact.validate().contains("Имя обязательно для заполнения"));
    }

    @Test
    void nonDigitInternalNumberProducesError() {
        CorporateContact contact = new CorporateContact(
                "Иван", "+7999", "i@e.ru", "ООО Р", "Инженер", "двенадцать");

        assertTrue(contact.validate().contains("Внутренний номер должен состоять из цифр"));
    }

    private static CorporateContact validCorporateContact() {
        return new CorporateContact("Иван Иванов", "+7999", "i@e.ru",
                "ООО Ромашка", "Инженер", "123");
    }
}