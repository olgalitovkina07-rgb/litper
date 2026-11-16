package ru.litper.persistence.serialize;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Содержимое бинарного файла для {@link ObjectOutputStream}.
 *
 * <p>Перед массивом записей в поток пишется метка формата и версия схемы.
 * Кастомные {@code writeObject}/{@code readObject} нужны, чтобы версия всегда
 * была в начале данных и проверялась до восстановления записей.</p>
 */
final class ContactBookFile implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Метка формата — четыре байта «LITP». */
    private static final int MAGIC = 0x4C495450;

    private int schemaVersion;
    private ContactRecord[] contacts;

    ContactBookFile(int schemaVersion, ContactRecord[] contacts) {
        this.schemaVersion = schemaVersion;
        this.contacts = contacts;
    }

    int schemaVersion() {
        return schemaVersion;
    }

    ContactRecord[] contacts() {
        return contacts;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeInt(MAGIC);
        out.writeInt(schemaVersion);
        out.writeObject(contacts);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        int magic = in.readInt();
        if (magic != MAGIC) {
            throw new InvalidObjectException("Файл не является справочником в бинарном формате");
        }
        int version = in.readInt();
        if (version > ContactData.CURRENT_VERSION) {
            throw new NewerSchemaVersionException(version);
        }
        if (version < ContactData.MIN_VERSION) {
            throw new InvalidObjectException("Неподдерживаемая версия файла: " + version);
        }
        Object value = in.readObject();
        if (!(value instanceof ContactRecord[] records)) {
            throw new InvalidObjectException("Повреждён бинарный файл: нет списка контактов");
        }
        this.schemaVersion = version;
        this.contacts = records;
    }
}
