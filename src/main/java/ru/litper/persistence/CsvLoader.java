package ru.litper.persistence;

import ru.litper.model.Contact;
import ru.litper.model.ContactType;
import ru.litper.model.CorporateContact;
import ru.litper.model.EmergencyContact;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;

/**
 * Читает справочник контактов из CSV.
 *
 * <p>Формат описан в README: первая строка — заголовок, разделитель {@code ;}.
 * Битые строки пропускаются, ошибка одной строки не останавливает загрузку файла.</p>
 *
 * <p>Ошибка чтения самого файла (например, файл не найден) бросается наверх
 * в виде {@link IOException} — и обрабатывается уже в GUI как диалог.</p>
 */
public final class CsvLoader {

    private static final int FIELD_COUNT = 7;
    private static final String HEADER = "type;name;phone;email;organization;position;internalNumber";

    /** Как часто сообщать прогресс загрузки (строк). */
    private static final int PROGRESS_STEP = 200;

    private static final int IDX_TYPE = 0;
    private static final int IDX_NAME = 1;
    private static final int IDX_PHONE = 2;
    private static final int IDX_EMAIL = 3;
    private static final int IDX_ORGANIZATION = 4;
    private static final int IDX_POSITION = 5;
    private static final int IDX_INTERNAL_NUMBER = 6;

    /**
     * Загружает все контакты из файла.
     *
     * @param file путь к CSV-файлу
     * @return результат загрузки с контактами и количеством пропущенных строк
     * @throws IOException если файл нельзя прочитать
     */
    public CsvLoadResult load(Path file) throws IOException {
        return load(file, null);
    }

    /**
     * Загружает все контакты из файла, сообщая прогресс.
     *
     * <p>Метод вызывается из фонового потока (GUI загружает большой файл через
     * {@code javafx.concurrent.Task}), поэтому прогресс — обычный callback,
     * а не обращения к JavaFX.</p>
     *
     * @param file     путь к CSV-файлу
     * @param progress получает количество уже прочитанных строк; может быть {@code null}
     * @return результат загрузки с контактами и количеством пропущенных строк
     * @throws IOException если файл нельзя прочитать
     */
    public CsvLoadResult load(Path file, LongConsumer progress) throws IOException {
        // Список здесь — только временный буфер разбора, результат отдаётся массивом.
        List<Contact> contacts = new ArrayList<>();
        int skipped = 0;

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            long lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                if (lineNumber == 1) {
                    continue; // строка-заголовок
                }
                try {
                    contacts.add(parseLine(line, lineNumber));
                } catch (CsvParseException e) {
                    skipped++;
                    System.err.println(e.getMessage());
                }
                if (progress != null && lineNumber % PROGRESS_STEP == 0) {
                    progress.accept(lineNumber);
                }
            }
        }
        if (progress != null) {
            progress.accept(countLines(file));
        }
        return new CsvLoadResult(contacts.toArray(new Contact[0]), skipped);
    }

    /** Всего строк в файле — нужно, чтобы прогресс был определённым, а не бесконечным. */
    public long countLines(Path file) throws IOException {
        try (var lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return lines.count();
        }
    }

    /**
     * Разбирает одну строку CSV в объект модели.
     *
     * @throws CsvParseException если строка битая
     */
    public Contact parseLine(String line, long lineNumber) throws CsvParseException {
        String[] parts = line.split(";", -1);
        if (parts.length != FIELD_COUNT) {
            throw new CsvParseException(CsvErrorCode.WRONG_FIELD_COUNT, lineNumber);
        }

        ContactType type = parseType(parts[IDX_TYPE], lineNumber);

        String name = parts[IDX_NAME].trim();
        String phone = parts[IDX_PHONE].trim();
        String email = parts[IDX_EMAIL].trim();
        String organization = parts[IDX_ORGANIZATION].trim();
        String position = parts[IDX_POSITION].trim();
        String internalNumber = parts[IDX_INTERNAL_NUMBER].trim();

        if (name.isEmpty() || phone.isEmpty()) {
            throw new CsvParseException(CsvErrorCode.EMPTY_FIELD, lineNumber);
        }

        return switch (type) {
            case EMERGENCY -> new EmergencyContact(name, phone, email, organization);
            case CORPORATE -> {
                if (!internalNumber.matches("\\d+")) {
                    throw new CsvParseException(CsvErrorCode.BAD_NUMBER, lineNumber);
                }
                yield new CorporateContact(name, phone, email, organization,
                        position, internalNumber);
            }
        };
    }

    private ContactType parseType(String rawType, long lineNumber) throws CsvParseException {
        try {
            return ContactType.fromCsvId(rawType.trim());
        } catch (IllegalArgumentException e) {
            throw new CsvParseException(CsvErrorCode.UNKNOWN_TYPE, lineNumber);
        }
    }
}