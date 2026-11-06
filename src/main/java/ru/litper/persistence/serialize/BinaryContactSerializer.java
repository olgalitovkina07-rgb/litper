package ru.litper.persistence.serialize;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Собственный бинарный формат справочника.
 *
 * <p>Файл начинается с «магии» (метки формата) и версии, дальше идёт число записей
 * и сами записи: строки {@link DataOutputStream#writeUTF(String)}, набор которых
 * зависит от версии схемы. Метка нужна, чтобы отличить свой файл от произвольного
 * и вежливо сообщить об ошибке.</p>
 */
public final class BinaryContactSerializer implements Serializer<ContactData> {

    private static final String FORMAT = "binary";
    private static final String EXTENSION = "bin";

    /** Метка формата — четыре байта «LITP». */
    private static final int MAGIC = 0x4C495450;

    /** Разумный предел на число записей: защита от повреждённого счётчика. */
    private static final int MAX_RECORDS = 10_000_000;

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
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(file)))) {
            out.writeInt(MAGIC);
            out.writeInt(version);
            out.writeInt(data.contacts().length);
            boolean withNote = version >= SchemaVersion.V2.number();
            boolean withTags = version >= SchemaVersion.V3.number();
            for (ContactRecord record : data.contacts()) {
                writeText(out, record.type());
                writeText(out, record.name());
                writeText(out, record.phone());
                writeText(out, record.email());
                writeText(out, record.organization());
                writeText(out, record.position());
                writeText(out, record.internalNumber());
                if (withNote) {
                    writeText(out, record.note());
                }
                if (withTags) {
                    writeText(out, record.tags());
                }
            }
        } catch (IOException e) {
            throw new SerializationException("Не удалось сохранить бинарный файл: " + e.getMessage(), e);
        }
    }

    @Override
    public ContactData load(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != MAGIC) {
                throw new SerializationException("Файл не является справочником в бинарном формате");
            }
            int version = in.readInt();
            if (!ContactData.isSupportedVersion(version)) {
                throw new SerializationException("Неподдерживаемая версия файла: " + version);
            }
            int count = in.readInt();
            if (count < 0 || count > MAX_RECORDS) {
                throw new SerializationException("Повреждён заголовок файла: неверное число записей");
            }
            boolean withNote = version >= SchemaVersion.V2.number();
            boolean withTags = version >= SchemaVersion.V3.number();
            ContactRecord[] records = new ContactRecord[count];
            for (int i = 0; i < count; i++) {
                String type = readText(in);
                String name = readText(in);
                String phone = readText(in);
                String email = readText(in);
                String organization = readText(in);
                String position = readText(in);
                String internalNumber = readText(in);
                String note = withNote ? readText(in) : null;
                String tags = withTags ? readText(in) : null;
                records[i] = new ContactRecord(type, name, phone, email, organization,
                        position, internalNumber, note, tags);
            }
            return new ContactData(version, records);
        } catch (SerializationException e) {
            throw e;
        } catch (EOFException e) {
            throw new SerializationException("Бинарный файл обрывается раньше, чем заканчиваются данные", e);
        } catch (IOException e) {
            throw new SerializationException("Не удалось прочитать бинарный файл: " + e.getMessage(), e);
        }
    }

    private static void writeText(DataOutputStream out, String value) throws IOException {
        out.writeUTF(value == null ? "" : value);
    }

    private static String readText(DataInputStream in) throws IOException {
        return in.readUTF();
    }
}
