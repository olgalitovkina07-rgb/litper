package ru.litper.persistence.serialize;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContactRecord(
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

    /** Старый формат записи без новых полей — нужен для совместимости и тестов. */
    public ContactRecord(String type, String name, String phone, String email,
                         String organization, String position, String internalNumber) {
        this(type, name, phone, email, organization, position, internalNumber, EMPTY, EMPTY);
    }

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
                    corporate.getInternalNumber(),
                    corporate.getNote(),
                    corporate.getTags());
        }
        if (contact instanceof EmergencyContact emergency) {
            return new ContactRecord(
                    ContactType.EMERGENCY.getCsvId(),
                    emergency.getName(),
                    emergency.getPhone(),
                    emergency.getEmail(),
                    emergency.getOrganization(),
                    EMPTY,
                    EMPTY,
                    emergency.getNote(),
                    emergency.getTags());
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
        String contactNote = orEmpty(note);
        String contactTags = orEmpty(tags);
        return switch (parsedType) {
            case EMERGENCY -> new EmergencyContact(contactName, contactPhone, contactEmail,
                    contactOrganization, contactNote, contactTags);
            case CORPORATE -> new CorporateContact(
                    contactName, contactPhone, contactEmail, contactOrganization,
                    orEmpty(position), orEmpty(internalNumber), contactNote, contactTags);
        };
    }

    private static String orEmpty(String value) {
        return value == null ? EMPTY : value;
    }
}
