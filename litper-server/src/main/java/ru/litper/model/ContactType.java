package ru.litper.model;

/**
 * Тип контакта, как он записывается в CSV-файле и отображается в GUI.
 */
public enum ContactType {

    CORPORATE("corporate", "Корпоративный"),
    EMERGENCY("emergency", "Аварийный");

    private final String csvId;
    private final String displayName;

    ContactType(String csvId, String displayName) {
        this.csvId = csvId;
        this.displayName = displayName;
    }

    /** Идентификатор для строки CSV. */
    public String getCsvId() {
        return csvId;
    }

    /** Человекочитаемое имя для GUI. */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Ищет тип по CSV-идентификатору.
     *
     * @throws IllegalArgumentException если идентификатор неизвестен
     */
    public static ContactType fromCsvId(String csvId) {
        for (ContactType type : values()) {
            if (type.csvId.equals(csvId)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Неизвестный тип контакта: " + csvId);
    }
}