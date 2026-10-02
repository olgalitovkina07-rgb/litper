package ru.litper.structure;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Конкурентный доступ к структуре: несколько потоков пишут в дерево,
 * пока другие читают снимки.
 *
 * <p>Защита в {@link Trie} — синхронизация публичных операций и то,
 * что читающие методы возвращают копии ({@code toArray()}, {@code lastTrace()}).
 * Без этого фоновый поток, перестраивающий дерево, мог бы отдать GUI-потоку
 * «наполовину перестроенную» структуру.</p>
 */
class TrieConcurrencyTest {

    private static final int THREADS = 8;
    private static final int INSERTS_PER_THREAD = 2_000;

    @Test
    void concurrentInsertsDoNotLoseValues() throws InterruptedException {
        Trie<String> trie = new Trie<>(String.class, value -> value);
        runConcurrently(threadIndex -> {
            for (int i = 0; i < INSERTS_PER_THREAD; i++) {
                trie.insert("контакт-" + threadIndex + "-" + i);
            }
        });

        assertEquals(THREADS * INSERTS_PER_THREAD, trie.size());
    }

    @Test
    void readersSeeConsistentSnapshotWhileTreeIsRebuilt() throws InterruptedException {
        Trie<String> trie = new Trie<>(String.class, value -> value);
        trie.insert("стабильный-контакт");

        runConcurrently(threadIndex -> {
            for (int i = 0; i < INSERTS_PER_THREAD; i++) {
                if (threadIndex == 0) {
                    trie.insert("мусор-" + i);
                } else {
                    String[] snapshot = trie.toArray();
                    assertTrue(snapshot.length >= 1);
                    assertEquals("стабильный-контакт", snapshot[0]);
                }
            }
        });

        assertEquals(1 + INSERTS_PER_THREAD, trie.size());
    }

    @Test
    void concurrentRemoveAndInsertKeepSizeConsistent() throws InterruptedException {
        Trie<String> trie = new Trie<>(String.class, value -> value);
        for (int i = 0; i < INSERTS_PER_THREAD; i++) {
            trie.insert("общий-" + i);
        }

        runConcurrently(threadIndex -> {
            for (int i = 0; i < INSERTS_PER_THREAD; i++) {
                if (threadIndex % 2 == 0) {
                    trie.remove("общий-" + i);
                } else {
                    trie.insert("свой-" + threadIndex + "-" + i);
                }
            }
        });

        // 2000 исходных удалены чётными потоками, нечётные добавили 4 × 2000
        assertEquals(4 * INSERTS_PER_THREAD, trie.size());
    }

    /** Запускает THREADS потоков и ждёт их завершения. */
    private static void runConcurrently(java.util.function.IntConsumer body) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        for (int t = 0; t < THREADS; t++) {
            int threadIndex = t;
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    body.accept(threadIndex);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            thread.setDaemon(true);
            thread.start();
        }
        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "Потоки не завершились за 60 секунд");
    }
}