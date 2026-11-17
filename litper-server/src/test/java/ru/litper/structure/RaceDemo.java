package ru.litper.structure;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Демонстрация гонки потоков (максимум лабораторной №2).
 *
 * <p>Показано два случая:</p>
 * <ol>
 *   <li>запись без защиты — теряются обновления: два потока делают
 *       {@code counter++} по 10 000 раз, а результат меньше 20 000;</li>
 *   <li>та же нагрузка на {@link Trie}, где все операции {@code synchronized},
 *       — {@code size()} совпадает с числом вставок, а читатели получают
 *       целостные снимки.</li>
 * </ol>
 *
 * <p>Запускается вручную (обычный {@code main}); вывод удобно вставить в отчёт.</p>
 */
public final class RaceDemo {

    private static final int THREADS = 2;
    private static final int ITERATIONS = 10_000;

    private static int counter; // намеренно без volatile — «незащищённая» переменная

    private RaceDemo() {
    }

    public static void main(String[] args) throws InterruptedException {
        int expected = THREADS * ITERATIONS;

        int lost = unsafeIncrement(expected);
        System.out.printf("Без синхронизации: ожидалось %d, получено %d — потеряно обновлений: %d%n",
                expected, lost, expected - lost);

        int actual = synchronizedTrie(expected);
        System.out.printf("Trie с synchronized: ожидалось %d, получено %d%n", expected, actual);
        System.out.println(actual == expected
                ? "Гонки нет: критическая секция защищена."
                : "Гонка обнаружена — защита не сработала.");
    }

    /** Два потока увеличивают общий счётчик без синхронизации. */
    private static int unsafeIncrement(int expected) throws InterruptedException {
        counter = 0;
        CountDownLatch start = new CountDownLatch(1);
        Thread[] threads = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) {
            threads[i] = new Thread(() -> {
                await(start);
                for (int n = 0; n < ITERATIONS; n++) {
                    counter++;
                }
            });
            threads[i].setDaemon(true);
            threads[i].start();
        }
        start.countDown();
        joinAll(threads);
        return counter;
    }

    /** Те же потоки пишут в Trie, где каждая операция синхронизирована. */
    private static int synchronizedTrie(int expected) throws InterruptedException {
        Trie<String> trie = new Trie<>(String.class, value -> value);
        CountDownLatch start = new CountDownLatch(1);
        Thread[] threads = new Thread[THREADS];
        for (int t = 0; t < THREADS; t++) {
            int threadIndex = t;
            threads[t] = new Thread(() -> {
                await(start);
                for (int n = 0; n < ITERATIONS; n++) {
                    trie.insert("контакт-" + threadIndex + "-" + n);
                }
            });
            threads[t].setDaemon(true);
            threads[t].start();
        }
        start.countDown();
        joinAll(threads);
        return trie.size();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void joinAll(Thread[] threads) throws InterruptedException {
        for (Thread thread : threads) {
            thread.join(TimeUnit.MINUTES.toMillis(1));
        }
    }
}