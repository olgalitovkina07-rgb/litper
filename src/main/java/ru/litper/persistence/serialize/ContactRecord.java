package ru.litper.persistence.serialize;

import ru.litper.model.Contact;
import ru.litper.model.ContactType;
import ru.litper.model.CorporateContact;
import ru.litper.model.EmergencyContact;

/**
 * Плоское представление контакта для сериализации.
 *
 * <p>Поля те же, что и в CSV: так JSON остаётся читаемым «глазами», а оба формата
 * хранят одинаковый набор данных. Полиморфизм модели разворачивается в поле
 * {@code type}, лишние для аварийного контакта поля пишутся пустыми строками.</p>
 */
public record ContactRecord(
        String type,
        String name,
        String phone,
        String email,
        String organization,
        String position,
        String internalNumber) {

    private static final String EMPTY = "";

    /** Превращает контакт модели в запись для файла. */
    public static ContactRecord of(Contact contact) {
        if (contact instanceof CorporateContact corporate) {
            return new ContactRecord(
                    ContactType.CORPORATE.getCsvId(),
                    corporate.getName(),
                    corporate.getPhone(),
                    corporate.getEmail(),
                    corporate.getOrganization(),
                    corporate.getPosition(),
                    corporate.getInternalNumber());
        }
        if (contact instanceof EmergencyContact emergency) {
            return new ContactRecord(
                    ContactType.EMERGENCY.getCsvId(),
                    emergency.getName(),
                    emergency.getPhone(),
                    emergency.getEmail(),
                    emergency.getOrganization(),
                    EMPTY,
                    EMPTY);
        }
        throw new IllegalArgumentException("Неизвестный тип контакта: " + contact.getClass());
    }

    /**
     * Восстанавливает контакт модели.
     *
     * @throws SerializationException если тип неизвестен или обязательные поля пустые
     */
    public Contact toContact() throws SerializationException {
        ContactType parsedType;
        try {
            parsedType = ContactType.fromCsvId(type == null ? EMPTY : type.trim());
        } catch (IllegalArgumentException e) {
            throw new SerializationException("Неизвестный тип контакта: " + type);
        }

        String contactName = orEmpty(name);
        String contactPhone = orEmpty(phone);
        if (contactName.isEmpty() || contactPhone.isEmpty()) {
            throw new SerializationException("У контакта не заполнены имя или телефон");
        }

        String contactEmail = orEmpty(email);
        String contactOrganization = orEmpty(organization);
        return switch (parsedType) {
            case EMERGENCY -> new EmergencyContact(contactName, contactPhone, contactEmail, contactOrganization);
            case CORPORATE -> new CorporateContact(
                    contactName, contactPhone, contactEmail, contactOrganization,
                    orEmpty(position), orEmpty(internalNumber));
        };
    }

    private static String orEmpty(String value) {
        return value == null ? EMPTY : value;
    }
}
