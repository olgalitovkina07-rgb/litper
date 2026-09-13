package ru.litper.model;

/**
 * Базовая сущность «Контакт».
 *
 * <p>Общие поля: имя, телефон, e-mail, организация.
 * От этого класса наследуются {@link EmergencyContact} и {@link CorporateContact}.</p>
 */
public class Contact {

    protected String name;
    protected String phone;
    protected String email;
    protected String organization;

    public Contact(String name, String phone, String email, String organization) {
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.organization = organization;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    @Override
    public String toString() {
        return name + phone;
    }
}