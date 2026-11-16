package ru.litper.persistence.serialize;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Бинарный формат справочника на основе {@link java.io.Serializable}.
 *
 * <p>Файл пишется через {@link ObjectOutputStream}: сначала идёт служебный
 * заголовок потока Java-сериализации, затем {@link ContactBookFile} с меткой
 * формата, версией схемы и массивом записей. Версия проверяется при чтении,
 * поэтому старый файл можно мигрировать, а чужой или повреждённый — отклонить
 * с понятной ошибкой.</p>
 */
public final class BinaryContactSerializer implements Serializer<ContactData> {

    private static final String FORMAT = "binary";
    private static final String EXTENSION = "bin";

    @Override
    public String format() {
        return FORMAT;
    }

    @Override
    public String fileExtension() {
        return EXTENSION;
    }

    @Override
    public void save(ContactData data, Path file) throws IOException {
        int version = data.schemaVersion();
        if (!ContactData.isSupportedVersion(version)) {
            throw new SerializationException("Неподдерживаемая версия файла: " + version);
        }
        if (data.contacts() == null) {
            throw new SerializationException("В файле нет списка контактов");
        }

        // Поля, которых не было в выбранной версии, не должны попасть в файл.
        ContactData versioned = data.forSchemaVersion(SchemaVersion.of(version));
        try (ObjectOutputStream out = new ObjectOutputStream(
                new BufferedOutputStream(Files.newOutputStream(file)))) {
            out.writeObject(new ContactBookFile(version, versioned.contacts()));
        } catch (IOException e) {
            throw new SerializationException("Не удалось сохранить бинарный файл: " + e.getMessage(), e);
        }
    }

    @Override
    public ContactData load(Path file) throws IOException {
        try (ObjectInputStream in = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(file)))) {
            Object value = in.readObject();
            if (!(value instanceof ContactBookFile book)) {
                throw new SerializationException(
                        "Файл не является справочником в бинарном формате");
            }
            return new ContactData(book.schemaVersion(), book.contacts());
        } catch (SerializationException e) {
            throw e;
        } catch (InvalidObjectException e) {
            throw new SerializationException(e.getMessage(), e);
        } catch (StreamCorruptedException | ClassNotFoundException e) {
            throw new SerializationException(
                    "Файл не является справочником в бинарном формате", e);
        } catch (EOFException e) {
            throw new SerializationException(
                    "Бинарный файл обрывается раньше, чем заканчиваются данные", e);
        } catch (IOException e) {
            throw new SerializationException(
                    "Не удалось прочитать бинарный файл: " + e.getMessage(), e);
        }
    }
}
