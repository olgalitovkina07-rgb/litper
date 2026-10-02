package ru.litper.structure;

import java.util.Arrays;

/**
 * Префиксное дерево (Trie) — собственная реализация на массивах и собственных узлах.
 *
 * <p>Коллекции {@code java.util} не используются: узлы — собственные
 * ({@link TrieNode}), дети и значения лежат в обычных массивах.</p>
 *
 * <p>Ключ берётся у значения через {@link KeyExtractor}. Все операции
 * {@code synchronized}: структуру читают и пишут фоновые потоки (загрузка CSV,
 * генерация данных), а GUI-поток читает снимки {@link #toArray()}.</p>
 *
 * <p>Каждая публичная операция запоминает «путь» посещённых узлов
 * ({@link #lastTrace()}) и текст операции ({@link #lastOperation()}) — это нужно
 * визуализации, чтобы подсветить посещённые при поиске/вставке узлы.</p>
 *
 * <p>В конструктор передаётся класс элемента: из-за стирания типов (type erasure)
 * массив {@code T[]} нельзя создать как {@code new Object[n]} — на присваивании
 * {@code String[] found = trie.findAll(...)} получился бы {@code ClassCastException}.
 * Создание через {@link java.lang.reflect.Array#newInstance} даёт массив нужного типа.</p>
 *
 * @param <T> тип хранимого значения
 */
public final class Trie<T> {

    /** Значение лимита в {@link #autocomplete(String, int)}, означающее «без ограничения». */
    public static final int NO_LIMIT = 0;

    private static final int INITIAL_TRACE_CAPACITY = 64;

    /** Ограничение трассировки: иначе на 10^5 контактах путь раздувает память. */
    private static final int MAX_TRACE_NODES = 4096;

    private final Class<T> elementType;
    private final KeyExtractor<T> keyExtractor;

    private TrieNode<T> root;
    private int size;
    private int nodeCount;

    private TrieNode<T>[] trace;
    private int traceSize;
    private String lastOperation = "";

    public Trie(Class<T> elementType, KeyExtractor<T> keyExtractor) {
        this.elementType = elementType;
        this.keyExtractor = keyExtractor;
        this.root = new TrieNode<>((char) 0, null);
        this.nodeCount = 1;
        this.trace = newTraceArray(INITIAL_TRACE_CAPACITY);
    }

    // ------------------------------------------------------------------ основные операции

    /**
     * Добавляет значение в дерево по его ключу.
     *
     * <p>Ключ может совпадать с уже существующим: значения накапливаются
     * в одном терминальном узле (имена контактов не обязаны быть уникальными).</p>
     *
     * @throws IllegalArgumentException если значение {@code null}
     * @return {@code false}, если ключ пустой и значение не добавлено
     */
    public synchronized boolean insert(T value) {
        if (value == null) {
            throw new IllegalArgumentException("Значение не должно быть null");
        }
        String key = keyExtractor.key(value);
        if (key == null || key.isEmpty()) {
            beginTrace("вставка: <пустой ключ>");
            return false;
        }

        beginTrace("вставка: " + key);
        TrieNode<T> node = root;
        node.incrementSubtree();
        for (int i = 0; i < key.length(); i++) {
            char symbol = key.charAt(i);
            int existing = node.indexOfChild(symbol);
            if (existing >= 0) {
                node = node.child(existing);
            } else {
                node = node.addChild(symbol);
                nodeCount++;
            }
            node.incrementSubtree();
            addToTrace(node);
        }
        node.addValue(value);
        size++;
        return true;
    }

    /**
     * Возвращает все значения с ключом {@code key} (пустой массив, если таких нет).
     */
    public synchronized T[] findAll(String key) {
        beginTrace("поиск: " + key);
        TrieNode<T> node = descend(key);
        if (node == null) {
            return emptyArray();
        }
        T[] result = newArray(node.terminalCount());
        System.arraycopy(node.values(), 0, result, 0, node.terminalCount());
        return result;
    }

    /** Первое значение с ключом {@code key} или {@code null}. */
    public synchronized T findFirst(String key) {
        T[] found = findAll(key);
        return found.length == 0 ? null : found[0];
    }

    /** Узел, соответствующий ключу, или {@code null} — для визуализации. */
    public synchronized TrieNode<T> findNode(String key) {
        beginTrace("поиск узла: " + key);
        return descend(key);
    }

    /** {@code true}, если в дереве есть хотя бы одно значение с таким ключом. */
    public synchronized boolean contains(String key) {
        beginTrace("проверка наличия: " + key);
        return descend(key) != null;
    }

    /**
     * Удаляет конкретное значение (по ссылке, затем по {@code equals}).
     *
     * @return {@code false}, если значения в дереве нет
     */
    public synchronized boolean remove(T value) {
        String key = keyExtractor.key(value);
        beginTrace("удаление: " + key);
        if (key == null || key.isEmpty()) {
            return false;
        }
        TrieNode<T> node = descend(key);
        if (node == null || !node.removeValue(value)) {
            return false;
        }
        size--;
        decrementSubtreeUpwards(node);
        pruneUpwards(node);
        return true;
    }

    /** Удаляет все значения с ключом {@code key}. */
    public synchronized boolean removeKey(String key) {
        beginTrace("удаление по ключу: " + key);
        TrieNode<T> node = descend(key);
        if (node == null || node.terminalCount() == 0) {
            return false;
        }
        int removed = node.terminalCount();
        node.clearValues();
        size -= removed;
        decrementSubtreeUpwards(node);
        pruneUpwards(node);
        return true;
    }

    /**
     * Удаляет всё поддерево, соответствующее префиксу (дополнительная операция варианта).
     *
     * @return сколько контактов удалено
     * @throws IllegalArgumentException если префикс пустой (тогда это уже {@link #clear()})
     */
    public synchronized int removePrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            throw new IllegalArgumentException("Префикс не должен быть пустым: используйте clear()");
        }
        beginTrace("удаление по префиксу: " + prefix);
        TrieNode<T> node = descend(prefix);
        if (node == null) {
            return 0;
        }
        int removed = node.subtreeCount();
        node.clearValues();
        size -= removed;
        decrementSubtreeUpwards(node);
        pruneUpwards(node);
        return removed;
    }

    /** Полностью очищает дерево. */
    public synchronized void clear() {
        root = new TrieNode<>((char) 0, null);
        size = 0;
        nodeCount = 1;
        beginTrace("очистка");
    }

    // ------------------------------------------------------------------ дополнительные операции

    /**
     * Автодополнение: все значения, ключ которых начинается с {@code prefix},
     * в лексикографическом порядке.
     *
     * @param limit максимум результатов, {@link #NO_LIMIT} — без ограничения
     */
    public synchronized T[] autocomplete(String prefix, int limit) {
        beginTrace("автодополнение: " + prefix);
        TrieNode<T> node = descend(prefix);
        if (node == null) {
            return emptyArray();
        }
        int capacity = limit == NO_LIMIT
                ? node.subtreeCount()
                : Math.max(1, Math.min(limit, node.subtreeCount()));
        Object[] buffer = new Object[Math.max(1, capacity)];
        int found = collect(node, buffer, limit, 0);
        T[] result = newArray(found);
        System.arraycopy(buffer, 0, result, 0, found);
        return result;
    }

    /**
     * Подсчёт контактов в поддереве префикса.
     *
     * <p>Счётчик поддерживается при вставке и удалении, поэтому после спуска ответ
     * считывается за O(1).</p>
     */
    public synchronized int countByPrefix(String prefix) {
        beginTrace("подсчёт по префиксу: " + prefix);
        TrieNode<T> node = descend(prefix);
        return node == null ? 0 : node.subtreeCount();
    }

    /** Количество значений в дереве. */
    public synchronized int size() {
        return size;
    }

    /** Количество узлов дерева (включая корень). */
    public synchronized int nodeCount() {
        return nodeCount;
    }

    public synchronized boolean isEmpty() {
        return size == 0;
    }

    /** Алфавитный снимок значений — используется, чтобы GUI читал структуру без гонки. */
    public synchronized T[] toArray() {
        T[] result = newArray(size);
        Object[] buffer = new Object[Math.max(1, size)];
        int found = collect(root, buffer, NO_LIMIT, 0);
        System.arraycopy(buffer, 0, result, 0, found);
        return result;
    }

    /** Корень дерева (только для чтения — менять узлы может лишь сам {@link Trie}). */
    public synchronized TrieNode<T> root() {
        return root;
    }

    // ------------------------------------------------------------------ трассировка для визуализации

    /** Узлы, посещённые последней операцией, в порядке обхода (копия). */
    public synchronized TrieNode<T>[] lastTrace() {
        TrieNode<T>[] copy = newTraceArray(traceSize);
        System.arraycopy(trace, 0, copy, 0, traceSize);
        return copy;
    }

    /** Текст последней операции — показывается в панели визуализации. */
    public synchronized String lastOperation() {
        return lastOperation;
    }

    // ------------------------------------------------------------------ внутренние методы

    /** Спуск по ключу с записью посещённых узлов в трассировку. */
    private TrieNode<T> descend(String key) {
        TrieNode<T> node = root;
        if (key == null) {
            return null;
        }
        for (int i = 0; i < key.length(); i++) {
            int index = node.indexOfChild(key.charAt(i));
            if (index < 0) {
                return null;
            }
            node = node.child(index);
            addToTrace(node);
        }
        return node;
    }

    /** Сбор значений поддерева в лексикографическом порядке (дети отсортированы). */
    private int collect(TrieNode<T> node, Object[] buffer, int limit, int filled) {
        if (limit != NO_LIMIT && filled >= limit) {
            return filled;
        }
        for (int i = 0; i < node.childCount() && (limit == NO_LIMIT || filled < limit); i++) {
            filled = collect(node.child(i), buffer, limit, filled);
        }
        for (int i = 0; i < node.terminalCount() && (limit == NO_LIMIT || filled < limit); i++) {
            if (filled == buffer.length) {
                break; // буфер рассчитан по subtreeCount/лимиту — защита на всякий случай
            }
            buffer[filled++] = node.valueAt(i);
            addToTrace(node);
        }
        return filled;
    }

    private void decrementSubtreeUpwards(TrieNode<T> node) {
        TrieNode<T> current = node;
        while (current != null) {
            current.decrementSubtree();
            current = current.parent();
        }
    }

    /** Отсоединяет пустые узлы от родителя (после удаления). */
    private void pruneUpwards(TrieNode<T> node) {
        TrieNode<T> current = node;
        while (current != null && !current.isRoot() && current.isPrunable()) {
            TrieNode<T> parent = current.parent();
            parent.removeChildAt(parent.indexOfChild(current.symbol()));
            nodeCount--;
            current = parent;
        }
    }

    private void beginTrace(String operation) {
        traceSize = 0;
        lastOperation = operation;
        trace[0] = root;
        traceSize = 1;
    }

    private void addToTrace(TrieNode<T> node) {
        if (traceSize >= MAX_TRACE_NODES) {
            return;
        }
        if (traceSize == trace.length) {
            trace = Arrays.copyOf(trace, trace.length * 2);
        }
        trace[traceSize++] = node;
    }

    @SuppressWarnings("unchecked")
    private T[] newArray(int size) {
        return (T[]) java.lang.reflect.Array.newInstance(elementType, size);
    }

    @SuppressWarnings("unchecked")
    private T[] emptyArray() {
        return (T[]) java.lang.reflect.Array.newInstance(elementType, 0);
    }

    @SuppressWarnings("unchecked")
    private static <T> TrieNode<T>[] newTraceArray(int size) {
        return (TrieNode<T>[]) new TrieNode<?>[size];
    }
}