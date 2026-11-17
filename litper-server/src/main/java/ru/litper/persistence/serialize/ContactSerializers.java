package ru.litper.persistence.serialize;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Доступ к сериализаторам справочника.
 *
 * <p>Хранит готовые реализации и выбирает нужную по расширению файла. GUI не
 * создаёт сериализаторы сам — он спрашивает подходящий по имени файла.</p>
 */
public final class ContactSerializers {

    private static final Serializer<ContactData> JSON = new JsonContactSerializer();
    private static final Serializer<ContactData> BINARY = new BinaryContactSerializer();

    private ContactSerializers() {
    }

    public static Serializer<ContactData> json() {
        return JSON;
    }

    public static Serializer<ContactData> binary() {
        return BINARY;
    }

    /** Формат по расширению файла; без известного расширения считается JSON. */
    public static Serializer<ContactData> forFile(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith("." + BINARY.fileExtension())) {
            return BINARY;
        }
        return JSON;
    }
}
