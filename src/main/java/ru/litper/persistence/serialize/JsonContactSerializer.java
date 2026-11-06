package ru.litper.persistence.serialize;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Сохранение и загрузка справочника в JSON через Jackson.
 *
 * <p>Файл пишется с отступами — так его удобно читать и показывать фрагмент
 * в отчёте. Поле {@code schema_version} хранит версию схемы; для файлов,
 * созданных до лабораторной №4, принимается и старое имя {@code schemaVersion}.</p>
 */
public final class JsonContactSerializer implements Serializer<ContactData> {

    private static final String FORMAT = "json";
    private static final String EXTENSION = "json";
    private static final String VERSION_FIELD = "schema_version";
    private static final String LEGACY_VERSION_FIELD = "schemaVersion";

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
        JsonNode root;
        try {
            root = mapper.readTree(file.toFile());
        } catch (IOException e) {
            throw new SerializationException("Файл не является корректным JSON справочника", e);
        }
        if (root == null || !root.isObject()) {
            throw new SerializationException("Файл не является корректным JSON справочника");
        }

        int version = readVersion(root);
        if (!ContactData.isSupportedVersion(version)) {
            throw new SerializationException("Неподдерживаемая версия файла: " + version);
        }

        // Приводим заголовок к одному виду: старые файлы без версии считаем версией 1.
        ObjectNode object = (ObjectNode) root;
        object.put(VERSION_FIELD, version);
        object.remove(LEGACY_VERSION_FIELD);

        try {
            return mapper.treeToValue(root, ContactData.class);
        } catch (IOException e) {
            throw new SerializationException("Файл не является корректным JSON справочника", e);
        }
    }

    private static int readVersion(JsonNode root) throws SerializationException {
        JsonNode versionNode = root.get(VERSION_FIELD);
        if (versionNode == null) {
            versionNode = root.get(LEGACY_VERSION_FIELD);
        }
        if (versionNode == null || versionNode.isNull()) {
            return SchemaVersion.MIN.number();
        }
        if (!versionNode.isInt()) {
            throw new SerializationException("В файле некорректно записана версия схемы");
        }
        return versionNode.intValue();
    }
}
