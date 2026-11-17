package ru.litper.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class EmergencyContactTest {

    @Test
    void emergencyContactIsReadOnlyNotEditable() {
        EmergencyContact contact = new EmergencyContact("Пожарная служба", "101", "", "МЧС");

        assertFalse(contact instanceof Editable);
    }
}