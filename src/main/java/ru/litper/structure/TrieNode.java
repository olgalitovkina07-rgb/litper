package ru.litper.structure;

/**
 * Узел префиксного дерева.
 *
 * <p>Собственный узел без использования коллекций {@code java.util}:
 * дети хранятся в обычном массиве, отсортированном по символу (поиск ребёнка —
 * бинарный поиск), значения терминального узла — тоже в собственном массиве.</p>
 *
 * <p>Поля {@code children}, {@code valueCount} и {@code subtreeCount} меняются только
 * классом {@link Trie}: наружу отдаются только геттеры, поэтому внешний код
 * (в том числе GUI-визуализация) не может испортить структуру.</p>
 *
 * @param <T> тип хранимого значения
 */
public class TrieNode<T> {

    private static final int INITIAL_CHILD_CAPACITY = 4;
    private static final int INITIAL_VALUE_CAPACITY = 2;

    /** Символ ребра, ведущего в узел. У корня равен {@code 0}. */
    private final char symbol;
    private final TrieNode<T> parent;
    private final int depth;

    private TrieNode<T>[] children;
    private int childCount;

    private Object[] values;
    private int valueCount;

    /** Сколько контактов хранится в этом поддереве (включая сам узел). */
    private int subtreeCount;

    TrieNode(char symbol, TrieNode<T> parent) {
        this.symbol = symbol;
        this.parent = parent;
        this.depth = parent == null ? 0 : parent.depth + 1;
        this.children = newChildArray(INITIAL_CHILD_CAPACITY);
        this.values = new Object[INITIAL_VALUE_CAPACITY];
    }

    // ---------------------------------------------------------------- публичный доступ (только чтение)

    /** Символ ребра, ведущего в узел. */
    public char symbol() {
        return symbol;
    }

    /** {@code true} для корня дерева. */
    public boolean isRoot() {
        return parent == null;
    }

    /** Глубина узла: у корня 0, у первого уровня 1. */
    public int depth() {
        return depth;
    }

    /** Родительский узел или {@code null} для корня. */
    public TrieNode<T> parent() {
        return parent;
    }

    /** Количество детей узла. */
    public int childCount() {
        return childCount;
    }

    /** Символ ребра к ребёнку с индексом {@code index} (дети отсортированы по символу). */
    public char childSymbol(int index) {
        return children[index].symbol;
    }

    /** Ребёнок с индексом {@code index} (дети отсортированы по символу). */
    public TrieNode<T> child(int index) {
        return children[index];
    }

    /** {@code true}, если в узле заканчивается хотя бы одно имя. */
    public boolean isTerminal() {
        return valueCount > 0;
    }

    /** Сколько контактов заканчивается в этом узле (имена могут совпадать). */
    public int terminalCount() {
        return valueCount;
    }

    /** Сколько контактов в поддереве этого узла. */
    public int subtreeCount() {
        return subtreeCount;
    }

    /** Контакт, заканчивающийся в этом узле, по индексу. */
    @SuppressWarnings("unchecked")
    public T valueAt(int index) {
        return (T) values[index];
    }

    // ---------------------------------------------------------------- внутренние операции (пакет ru.litper.structure)

    /**
     * Ищет позицию ребёнка по символу.
     *
     * @return индекс существующего ребёнка либо отрицательное значение
     *         {@code -(позиция вставки) - 1}
     */
    int indexOfChild(char childSymbol) {
        int low = 0;
        int high = childCount - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            char midSymbol = children[mid].symbol;
            if (midSymbol == childSymbol) {
                return mid;
            }
            if (midSymbol < childSymbol) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return -(low + 1);
    }

    /** Возвращает существующего ребёнка или создаёт нового, сохраняя сортировку массива. */
    TrieNode<T> addChild(char childSymbol) {
        int found = indexOfChild(childSymbol);
        if (found >= 0) {
            return children[found];
        }
        int insertAt = -found - 1;
        ensureChildCapacity(childCount + 1);
        System.arraycopy(children, insertAt, children, insertAt + 1, childCount - insertAt);
        children[insertAt] = new TrieNode<>(childSymbol, this);
        childCount++;
        return children[insertAt];
    }

    void removeChildAt(int index) {
        System.arraycopy(children, index + 1, children, index, childCount - index - 1);
        children[childCount - 1] = null;
        childCount--;
    }

    void addValue(T value) {
        if (valueCount == values.length) {
            Object[] grown = new Object[values.length * 2];
            System.arraycopy(values, 0, grown, 0, valueCount);
            values = grown;
        }
        values[valueCount++] = value;
    }

    /** Удаляет значение: сначала по ссылке, затем по {@code equals}. */
    boolean removeValue(T value) {
        for (int i = 0; i < valueCount; i++) {
            if (values[i] == value) {
                removeValueAt(i);
                return true;
            }
        }
        for (int i = 0; i < valueCount; i++) {
            if (values[i].equals(value)) {
                removeValueAt(i);
                return true;
            }
        }
        return false;
    }

    void removeValueAt(int index) {
        System.arraycopy(values, index + 1, values, index, valueCount - index - 1);
        values[--valueCount] = null;
    }

    /** Удаляет все значения узла (удаление целого поддерева по префиксу). */
    void clearValues() {
        while (valueCount > 0) {
            removeValueAt(valueCount - 1);
        }
    }

    /** Значения терминального узла; массив используется только внутри пакета. */
    Object[] values() {
        return values;
    }

    void incrementSubtree() {
        subtreeCount++;
    }

    void decrementSubtree() {
        subtreeCount--;
    }

    /** Узел пуст: в нём нет ни имён, ни детей — его можно отсоединить от родителя. */
    boolean isPrunable() {
        return valueCount == 0 && childCount == 0;
    }

    private void ensureChildCapacity(int required) {
        if (required <= children.length) {
            return;
        }
        int capacity = Math.max(required, children.length * 2);
        TrieNode<T>[] grown = newChildArray(capacity);
        System.arraycopy(children, 0, grown, 0, childCount);
        children = grown;
    }

    @SuppressWarnings("unchecked")
    private static <T> TrieNode<T>[] newChildArray(int capacity) {
        return (TrieNode<T>[]) new TrieNode<?>[capacity];
    }
}