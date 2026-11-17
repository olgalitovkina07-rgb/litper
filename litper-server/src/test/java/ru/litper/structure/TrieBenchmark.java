package ru.litper.structure;

import java.util.Random;
import java.util.TreeMap;
import java.util.function.LongSupplier;

/**
 * Сравнительный обзор (максимум лабораторной №2): собственное префиксное дерево
 * против ближайшего аналога из {@code java.util}.
 *
 * <p>Ближайший аналог Trie в стандартной библиотеке — {@link TreeMap}:
 * он тоже даёт доступ по ключу с сохранением порядка, а префиксный запрос
 * выполняется обходом диапазона {@code tailMap(prefix, true)}.</p>
 *
 * <p>У аналога есть и честное ограничение: {@code TreeMap} хранит один ключ —
 * контакты с одинаковым именем схлопываются, а Trie держит все значения
 * в терминальном узле.</p>
 *
 * <p>Запускается вручную (обычный {@code main}), в приложении не участвует:
 * использование аналога из {@code java.util} в основном коде запрещено методичкой.</p>
 */
public final class TrieBenchmark {

    private static final int[] SIZES = {10_000, 100_000};
    private static final int REPEATS = 5;
    private static final String[] SEARCH_KEYS = {"Иван", "Мария", "Пётр", "Анна", "Борис"};
    private static final int AUTOCOMPLETE_LIMIT = 50;
    private static final long SEED = 42L;

    private TrieBenchmark() {
    }

    public static void main(String[] args) {
        System.out.println("Операция                    |     N | своё Trie | TreeMap (java.util) | отношение");
        System.out.println("----------------------------+-------+-----------+---------------------+---------");

        for (int size : SIZES) {
            String[] keys = generateKeys(size);

            row("вставка", size,
                    () -> insertIntoTrie(keys), () -> insertIntoMap(keys));
            row("поиск по имени", size,
                    () -> findInTrie(keys), () -> findInMap(keys));
            row("автодополнение (50)", size,
                    () -> autocompleteInTrie(keys), () -> autocompleteInMap(keys));
            row("подсчёт по префиксу", size,
                    () -> countInTrie(keys), () -> countInMap(keys));
            row("удаление", size,
                    () -> removeFromTrie(keys), () -> removeFromMap(keys));
            System.out.println();
        }
    }

    private static void row(String title, int size, LongSupplier trieOp, LongSupplier mapOp) {
        double trieMillis = average(trieOp);
        double mapMillis = average(mapOp);
        String ratio = mapMillis == 0 ? "—" : String.format("%.2fx", trieMillis / mapMillis);
        System.out.printf("%-27s| %6d| %8.2f мс| %17.2f мс| %8s%n",
                title, size, trieMillis, mapMillis, ratio);
    }

    /** Среднее время REPEATS прогонов, миллисекунды. */
    private static double average(LongSupplier operation) {
        long total = 0;
        for (int run = 0; run < REPEATS; run++) {
            total += operation.getAsLong();
        }
        return (total / (double) REPEATS) / 1_000_000.0;
    }

    // ------------------------------------------------------------------ своё дерево

    private static long insertIntoTrie(String[] keys) {
        Trie<String> trie = new Trie<>(String.class, value -> value);
        long start = System.nanoTime();
        for (String key : keys) {
            trie.insert(key);
        }
        return System.nanoTime() - start;
    }

    private static long findInTrie(String[] keys) {
        Trie<String> trie = filledTrie(keys);
        long start = System.nanoTime();
        for (String key : SEARCH_KEYS) {
            trie.findAll(key);
        }
        return System.nanoTime() - start;
    }

    private static long autocompleteInTrie(String[] keys) {
        Trie<String> trie = filledTrie(keys);
        long start = System.nanoTime();
        for (String key : SEARCH_KEYS) {
            trie.autocomplete(key, AUTOCOMPLETE_LIMIT);
        }
        return System.nanoTime() - start;
    }

    private static long countInTrie(String[] keys) {
        Trie<String> trie = filledTrie(keys);
        long start = System.nanoTime();
        for (String key : SEARCH_KEYS) {
            trie.countByPrefix(key);
        }
        return System.nanoTime() - start;
    }

    private static long removeFromTrie(String[] keys) {
        Trie<String> trie = filledTrie(keys);
        long start = System.nanoTime();
        for (String key : SEARCH_KEYS) {
            trie.removeKey(key);
        }
        return System.nanoTime() - start;
    }

    private static Trie<String> filledTrie(String[] keys) {
        Trie<String> trie = new Trie<>(String.class, value -> value);
        for (String key : keys) {
            trie.insert(key);
        }
        return trie;
    }

    // ------------------------------------------------------------------ аналог java.util

    private static long insertIntoMap(String[] keys) {
        TreeMap<String, String> map = new TreeMap<>();
        long start = System.nanoTime();
        for (String key : keys) {
            map.put(key, key);
        }
        return System.nanoTime() - start;
    }

    private static long findInMap(String[] keys) {
        TreeMap<String, String> map = filledMap(keys);
        long start = System.nanoTime();
        for (String key : SEARCH_KEYS) {
            map.get(key);
        }
        return System.nanoTime() - start;
    }

    /** Префиксный запрос аналога: спуск по {@code tailMap} до первого несовпадения. */
    private static long autocompleteInMap(String[] keys) {
        TreeMap<String, String> map = filledMap(keys);
        long start = System.nanoTime();
        for (String prefix : SEARCH_KEYS) {
            int found = 0;
            for (String candidate : map.tailMap(prefix, true).keySet()) {
                if (!candidate.startsWith(prefix) || found >= AUTOCOMPLETE_LIMIT) {
                    break;
                }
                found++;
            }
        }
        return System.nanoTime() - start;
    }

    private static long countInMap(String[] keys) {
        TreeMap<String, String> map = filledMap(keys);
        long start = System.nanoTime();
        for (String prefix : SEARCH_KEYS) {
            map.subMap(prefix, true, prefix + '￿', true).size();
        }
        return System.nanoTime() - start;
    }

    private static long removeFromMap(String[] keys) {
        TreeMap<String, String> map = filledMap(keys);
        long start = System.nanoTime();
        for (String key : SEARCH_KEYS) {
            map.remove(key);
        }
        return System.nanoTime() - start;
    }

    private static TreeMap<String, String> filledMap(String[] keys) {
        TreeMap<String, String> map = new TreeMap<>();
        for (String key : keys) {
            map.put(key, key);
        }
        return map;
    }

    /** Ключи того же вида, что и контакты приложения: «Имя Фамилия». */
    private static String[] generateKeys(int count) {
        String[] first = {"Иван", "Пётр", "Анна", "Мария", "Сергей", "Ольга", "Дмитрий", "Елена"};
        String[] last = {"Иванов", "Петров", "Сидоров", "Кузнецова", "Смирнов", "Попова", "Волков"};
        Random random = new Random(SEED);
        String[] keys = new String[count];
        for (int i = 0; i < count; i++) {
            keys[i] = first[random.nextInt(first.length)] + " " + last[random.nextInt(last.length)];
        }
        return keys;
    }
}