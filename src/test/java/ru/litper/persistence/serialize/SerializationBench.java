package ru.litper.persistence.serialize;

import ru.litper.model.Contact;
import ru.litper.service.ContactGenerator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.LongSupplier;

/**
 * Сравнение форматов для отчёта лабораторной №3: JSON против бинарного.
 *
 * <p>Показывает размер файла на заданном числе записей и среднее время
 * сохранения/загрузки за 5 прогонов, а в конце печатает фрагмент каждого файла —
 * так видно, как формат выглядит «глазами».</p>
 *
 * <p>Запускается вручную: {@code gradlew.bat serializationBench [количество]}.</p>
 */
public final class SerializationBench {

    private static final int DEFAULT_COUNT = 10_000;
    private static final int REPEATS = 5;
    private static final long SEED = 42L;

    /** Сколько символов JSON и байт бинарного файла показывать в отчёте. */
    private static final int FRAGMENT_CHARS = 300;
    private static final int FRAGMENT_BYTES = 64;

    private SerializationBench() {
    }

    public static void main(String[] args) throws IOException {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_COUNT;
        Contact[] contacts = new ContactGenerator().generate(count, SEED, null);
        ContactData data = ContactData.of(contacts);

        Path dir = Files.createTempDirectory("litper-format-bench");
        Path jsonFile = dir.resolve("contacts.json");
        Path binFile = dir.resolve("contacts.bin");

        Serializer<ContactData> json = ContactSerializers.json();
        Serializer<ContactData> binary = ContactSerializers.binary();

        json.save(data, jsonFile);
        binary.save(data, binFile);

        System.out.println("Набор: " + count + " контактов, повторов на замер: " + REPEATS);
        System.out.println();
        System.out.println("Формат | размер файла | сохранение | загрузка");
        System.out.println("-------+--------------+------------+---------");
        row("json", data, jsonFile, json);
        row("binary", data, binFile, binary);
        System.out.println();

        printJsonFragment(jsonFile);
        printBinaryFragment(binFile);
        System.out.println();
        System.out.println("Файлы: " + dir);
    }

    private static void row(String format, ContactData data, Path file,
                            Serializer<ContactData> serializer) throws IOException {
        double saveMillis = average(() -> time(() -> serializer.save(data, file)));
        double loadMillis = average(() -> time(() -> serializer.load(file)));
        System.out.printf("%-6s | %11s | %9.2f мс | %6.2f мс%n",
                format, Files.size(file) + " байт", saveMillis, loadMillis);
    }

    /** Среднее время REPEATS прогонов в миллисекундах. */
    private static double average(LongSupplier operation) {
        // прогревочный прогон не учитывается, чтобы JIT не занижал первое измерение
        operation.getAsLong();
        long total = 0;
        for (int run = 0; run < REPEATS; run++) {
            total += operation.getAsLong();
        }
        return (total / (double) REPEATS) / 1_000_000.0;
    }

    private static long time(FileAction action) {
        long start = System.nanoTime();
        try {
            action.run();
        } catch (IOException e) {
            throw new UncheckedIOException("Ошибка операции с файлом", e);
        }
        return System.nanoTime() - start;
    }

    /** Работа с файлом, которая бросает checked-исключение. */
    @FunctionalInterface
    private interface FileAction {
        void run() throws IOException;
    }

    private static void printJsonFragment(Path file) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        String fragment = content.substring(0, Math.min(FRAGMENT_CHARS, content.length()));
        System.out.println("JSON (фрагмент):");
        System.out.println(fragment);
        if (content.length() > FRAGMENT_CHARS) {
            System.out.println("…");
        }
    }

    private static void printBinaryFragment(Path file) throws IOException {
        byte[] all = Files.readAllBytes(file);
        int length = Math.min(FRAGMENT_BYTES, all.length);
        System.out.println("Бинарный формат (первые " + length + " байт, hex):");
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < length; i++) {
            hex.append(String.format("%02X ", all[i]));
            if ((i + 1) % 16 == 0) {
                hex.append(System.lineSeparator());
            }
        }
        System.out.println(hex);
    }
}
