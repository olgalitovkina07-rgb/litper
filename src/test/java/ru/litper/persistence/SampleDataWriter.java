package ru.litper.persistence;

import ru.litper.model.Contact;
import ru.litper.model.EmergencyContact;
import ru.litper.service.ContactGenerator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Создаёт большой тестовый CSV-файл для ручной проверки приложения.
 *
 * <p>Запуск: {@code gradlew.bat sampleCsv} (по умолчанию 10 000 контактов,
 * путь {@code data/contacts-large.csv}, количество можно передать аргументом).</p>
 *
 * <p>Файл содержит:</p>
 * <ul>
 *   <li>корпоративные контакты, сгенерированные тем же {@link ContactGenerator},
 *       что и кнопка «Сгенерировать» в GUI;</li>
 *   <li>несколько аварийных контактов (read-only) — их нельзя создать через GUI;</li>
 *   <li>несколько заведомо битых строк — чтобы было видно, что загрузка их
 *       пропускает и продолжает читать файл дальше.</li>
 * </ul>
 */
public final class SampleDataWriter {

    /** Количество контактов по умолчанию. */
    private static final int DEFAULT_COUNT = 10_000;

    private static final long SEED = 42L;
    private static final String PATH_ARGUMENT = "data/contacts-large.csv";

    /** Аварийные контакты: их нельзя создать через GUI, только загрузить из файла. */
    private static final EmergencyContact[] EMERGENCY_CONTACTS = {
            new EmergencyContact("Пожарная служба", "101", "01@mchs.ru", "МЧС России"),
            new EmergencyContact("Скорая помощь", "103", "03@sklonos.ru", "Скорая медицинская помощь"),
            new EmergencyContact("Полиция", "102", "02@mvd.ru", "МВД России"),
            new EmergencyContact("Аварийная служба газа", "104", "gas@gaz.ru", "Мосгаз"),
            new EmergencyContact("Аварийная служба воды", "105", "vod@vod.ru", "Мосводоканал"),
            new EmergencyContact("Служба спасения", "112", "112@spas.ru", "Единая служба спасения"),
            new EmergencyContact("Охрана трубопроводов", "8-495-000-11-22", "", "Газпром"),
            new EmergencyContact("Диспетчерская энергоснабжения", "8-495-000-33-44", "", "Мосэнергосбыт"),
            new EmergencyContact("Техническая поддержка", "8-800-555-01-02", "help@example.com", "АО Пример"),
            new EmergencyContact("Дежурный администратор", "8-495-000-77-88", "", "БЦ Северный"),
    };

    /** Битые строки: неверное число полей, неизвестный тип, нечисловой номер. */
    private static final String[] BROKEN_LINES = {
            "corporate;Мария Иванова;+79990000100;maria@example.com;ООО Ромашка;Менеджер", // 6 полей
            "unknown;Пётр Петров;+79990000200;petr@example.com;ООО Ромашка;Инженер;10", // неизвестный тип
            "corporate;Сидор Сидоров;+79990000300;sidor@example.com;ООО Ромашка;Инженер;не число", // не тот номер
            "emergency;;+79990000400;;;", // пустое имя
            "corporate;Анна Аннина;;anna@example.com;ООО Ромашка;Менеджер;11", // пустой телефон
            "corporate;Борис Борисов;+79990000600;boris@example.com;ООО Ромашка;Инженер;12;лишнее поле",
            "  ", // пустая строка — просто пропускается
            "emergency;Вера Верена;+79990000700;vera@example.com;ООО Ромашка", // 5 полей вместо 7
    };

    private SampleDataWriter() {
    }

    public static void main(String[] args) throws Exception {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_COUNT;
        Path file = Path.of(args.length > 1 ? args[1] : PATH_ARGUMENT);

        Contact[] generated = new ContactGenerator().generate(count, SEED, null);
        List<Contact> contacts = new ArrayList<>();
        for (Contact contact : generated) {
            contacts.add(contact);
        }
        for (int i = 0; i < EMERGENCY_CONTACTS.length; i++) {
            int position = (int) ((long) contacts.size() * (i + 1) / (EMERGENCY_CONTACTS.length + 1));
            contacts.add(position, EMERGENCY_CONTACTS[i]);
        }

        new CsvWriter().write(file, contacts.toArray(new Contact[0]));
        appendBrokenLines(file);

        CsvLoader loader = new CsvLoader();
        CsvLoadResult check = loader.load(file);
        System.out.println("Файл: " + file.toAbsolutePath());
        System.out.println("Строк в файле (с заголовком): " + loader.countLines(file));
        System.out.println("Ожидалось контактов: " + contacts.size() + ", загружено: "
                + check.contacts().length + ", пропущено битых строк: " + check.skippedLines());
    }

    /** Дописывает битые строки в конец файла. */
    private static void appendBrokenLines(Path file) throws Exception {
        String text = String.join(System.lineSeparator(), BROKEN_LINES);
        Files.writeString(file, text, StandardCharsets.UTF_8,
                StandardOpenOption.WRITE, StandardOpenOption.APPEND);
    }
}