package ru.litper.persistence.serialize;

/**
 * Версии схемы файла справочника.
 *
 * <p>Версия 1 — исходные поля контакта. Версия 2 добавила заметку, версия 3 — теги.
 * Более старые файлы при импорте проходят цепочку миграций, а при экспорте можно
 * осознанно сохранить файл в совместимом старом формате.</p>
 */
public enum SchemaVersion {

    V1(1, "1 — без заметки и тегов", "заметки и теги"),
    V2(2, "2 — с заметкой, без тегов", "теги"),
    V3(3, "3 — текущая (заметка и теги)", "");

    public static final SchemaVersion MIN = V1;
    public static final SchemaVersion CURRENT = V3;

    private final int number;
    private final String displayName;
    private final String lostFields;

    SchemaVersion(int number, String displayName, String lostFields) {
        this.number = number;
        this.displayName = displayName;
        this.lostFields = lostFields;
    }

    public int number() {
        return number;
    }

    public String lostFields() {
        return lostFields;
    }

    public boolean hasNote() {
        return number >= V2.number;
    }

    public boolean hasTags() {
        return number >= V3.number;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
