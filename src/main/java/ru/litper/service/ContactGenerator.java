package ru.litper.service;

import ru.litper.model.Contact;
import ru.litper.model.CorporateContact;

import java.util.Random;
import java.util.concurrent.CancellationException;

/**
 * Генератор набора данных для структуры (требование лабораторной №2).
 *
 * <p>Генерируются только редактируемые контакты ({@link CorporateContact}):
 * read-only-типы, как и в лабораторной №1, появляются только из файла.</p>
 *
 * <p>Генерация детерминирована: одинаковые {@code count} и {@code seed} дают
 * одинаковый набор — это удобно для замеров и тестов.</p>
 */
public final class ContactGenerator {

    /** Отчёт о прогрессе; {@code false} в ответе означает «отменить операцию». */
    @FunctionalInterface
    public interface Progress {

        /**
         * @param done  сколько контактов уже сгенерировано
         * @param total сколько нужно всего
         * @return {@code false} — операцию следует прервать
         */
        boolean onProgress(int done, int total);
    }

    private static final String[] FIRST_NAMES = {
            "Иван", "Пётр", "Анна", "Мария", "Сергей", "Ольга", "Дмитрий", "Елена",
            "Николай", "Татьяна", "Алексей", "Ирина", "Владимир", "Юлия", "Борис", "Марина"
    };

    private static final String[] LAST_NAMES = {
            "Иванов", "Петров", "Сидоров", "Кузнецова", "Смирнов", "Попова", "Волков",
            "Соколова", "Лебедев", "Козлова", "Новиков", "Морозова", "Зайцев", "Павлова"
    };

    private static final String[] ORGANIZATIONS = {
            "ООО Ромашка", "АО Весна", "ЗАО Меридиан", "ООО Компас", "АО Север", "ООО Маяк"
    };

    private static final String[] POSITIONS = {
            "Инженер", "Менеджер", "Аналитик", "Техник", "Директор", "Бухгалтер"
    };

    /** Как часто сообщать прогресс при генерации. */
    private static final int PROGRESS_STEP = 500;

    private static final int PHONE_DIGITS = 9;
    private static final int EMAIL_SUFFIX_DIGITS = 4;
    private static final int INTERNAL_NUMBER_MIN = 100;
    private static final int INTERNAL_NUMBER_RANGE = 900;

    /**
     * Генерирует {@code count} корпоративных контактов.
     *
     * @param count    сколько контактов нужно
     * @param seed     seed генератора (одинаковый seed — одинаковые данные)
     * @param progress callback прогресса; может быть {@code null}
     * @return массив сгенерированных контактов
     * @throws CancellationException если {@code progress} попросил остановиться
     */
    public Contact[] generate(int count, long seed, Progress progress) {
        if (count <= 0) {
            return new Contact[0];
        }
        Random random = new Random(seed);
        Contact[] result = new Contact[count];

        for (int i = 0; i < count; i++) {
            result[i] = nextContact(random);
            int done = i + 1;
            if (progress != null && done % PROGRESS_STEP == 0 && !progress.onProgress(done, count)) {
                throw new CancellationException("Генерация отменена пользователем");
            }
        }
        if (progress != null) {
            progress.onProgress(count, count);
        }
        return result;
    }

    private static CorporateContact nextContact(Random random) {
        String name = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)]
                + " "
                + LAST_NAMES[random.nextInt(LAST_NAMES.length)];
        String phone = "+7" + randomDigits(random, PHONE_DIGITS);
        String email = "user" + randomDigits(random, EMAIL_SUFFIX_DIGITS) + "@example.ru";
        String organization = ORGANIZATIONS[random.nextInt(ORGANIZATIONS.length)];
        String position = POSITIONS[random.nextInt(POSITIONS.length)];
        String internalNumber = String.valueOf(INTERNAL_NUMBER_MIN + random.nextInt(INTERNAL_NUMBER_RANGE));
        return new CorporateContact(name, phone, email, organization, position, internalNumber);
    }

    private static String randomDigits(Random random, int count) {
        StringBuilder digits = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            digits.append(random.nextInt(10));
        }
        return digits.toString();
    }
}