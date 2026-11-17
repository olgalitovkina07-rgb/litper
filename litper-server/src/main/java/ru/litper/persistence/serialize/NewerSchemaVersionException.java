package ru.litper.persistence.serialize;

/**
 * Файл создан более новой версией приложения и не может быть прочитан.
 */
public class NewerSchemaVersionException extends SerializationException {

    private final int fileVersion;

    public NewerSchemaVersionException(int fileVersion) {
        super("Неподдерживаемая версия файла: " + fileVersion);
        this.fileVersion = fileVersion;
    }

    public int getFileVersion() {
        return fileVersion;
    }
}
