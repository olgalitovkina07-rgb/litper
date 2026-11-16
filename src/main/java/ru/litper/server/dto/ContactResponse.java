package ru.litper.server.dto;

import ru.litper.model.Contact;
import ru.litper.model.ContactType;
import ru.litper.model.CorporateContact;
import ru.litper.model.EmergencyContact;

/**
 * Представление контакта в REST-ответах.
 */
public record ContactResponse(
        long id,
        String type,
        String name,
        String phone,
        String email,
        String organization,
        String position,
        String internalNumber,
        String note,
        String tags) {

    private static final String EMPTY = "";

    public static ContactResponse from(long id, Contact contact) {
        if (contact instanceof CorporateContact corporate) {
            return new ContactResponse(
                    id,
                    ContactType.CORPORATE.getCsvId(),
                    corporate.getName(),
                    corporate.getPhone(),
                    value(corporate.getEmail()),
                    value(corporate.getOrganization()),
                    value(corporate.getPosition()),
                    value(corporate.getInternalNumber()),
                    value(corporate.getNote()),
                    value(corporate.getTags()));
        }
        if (contact instanceof EmergencyContact emergency) {
            return new ContactResponse(
                    id,
                    ContactType.EMERGENCY.getCsvId(),
                    emergency.getName(),
                    emergency.getPhone(),
                    value(emergency.getEmail()),
                    value(emergency.getOrganization()),
                    EMPTY,
                    EMPTY,
                    value(emergency.getNote()),
                    value(emergency.getTags()));
        }
        throw new IllegalArgumentException("Неизвестный тип контакта: " + contact.getClass());
    }

    private static String value(String text) {
        return text == null ? EMPTY : text;
    }
}
