package ru.litper.persistence.serialize;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Сохранение и загрузка справочника в JSON через Jackson.
 *
 * <p>Файл пишется с отступами — так его удобно читать и показывать фрагмент
 * в отчёте. Поле {@code schemaVersion} хранит версию схемы.</p>
 */
public final class JsonContactSerializer implements Serializer<ContactData> {

    private static final String FORMAT = "json";
    private static final String EXTENSION = "json";

    private final ObjectMapper mapper = new ObjectMapper();

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
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), data);
        } catch (IOException e) {
            throw new SerializationException("Не удалось сохранить JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public ContactData load(Path file) throws IOException {
        try {
            return mapper.readValue(file.toFile(), ContactData.class);
        } catch (IOException e) {
            throw new SerializationException("Файл не является корректным JSON справочника", e);
        }
    }
}
