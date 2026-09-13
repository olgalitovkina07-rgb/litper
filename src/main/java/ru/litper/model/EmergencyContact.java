package ru.litper.model;

/**
 * Аварийный контакт (пожарная, скорая, полиция и т. п.).
 *
 * <p>Read-only тип: не реализует {@link Editable},
 * поэтому не может быть создан или изменён через GUI — появляется только из файла CSV.</p>
 */
public class EmergencyContact extends Contact {

    public EmergencyContact(String name, String phone, String email, String organization) {
        super(name, phone, email, organization);
    }
}