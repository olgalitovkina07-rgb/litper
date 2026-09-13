package ru.litper.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Корпоративный контакт сотрудника.
 *
 * <p>Редактируемый тип: дополнительно к полям {@link Contact} содержит
 * должность и внутренний номер и реализует {@link Editable}.</p>
 */
public class CorporateContact extends Contact implements Editable {

    private String position;
    private String internalNumber;

    public CorporateContact(String name, String phone, String email,
                            String organization, String position, String internalNumber) {
        super(name, phone, email, organization);
        this.position = position;
        this.internalNumber = internalNumber;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getInternalNumber() {
        return internalNumber;
    }

    public void setInternalNumber(String internalNumber) {
        this.internalNumber = internalNumber;
    }

    @Override
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        if (isBlank(name)) {
            errors.add("Имя обязательно для заполнения");
        }
        if (isBlank(phone)) {
            errors.add("Телефон обязателен для заполнения");
        }
        if (isBlank(position)) {
            errors.add("Должность обязательна для заполнения");
        }
        if (isBlank(internalNumber)) {
            errors.add("Внутренний номер обязателен для заполнения");
        } else if (!internalNumber.matches("\\d+")) {
            errors.add("Внутренний номер должен состоять из цифр");
        }
        return errors;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}