package ru.litper.structure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Дополнительные операции варианта 4: автодополнение по префиксу
 * и подсчёт контактов в поддереве.
 *
 * <p>Ключи в нижнем регистре: само дерево сравнивает символы как есть,
 * а приведение к нижнему регистру делает сервис {@code ContactService}.</p>
 */
class TriePrefixOpsTest {

    private final Trie<String> trie = new Trie<>(String.class, value -> value);

    private void fill() {
        trie.insert("мария");
        trie.insert("марина");
        trie.insert("марк");
        trie.insert("наташа");
        trie.insert("наталья");
        trie.insert("пётр");
    }

    @Test
    void autocompleteCollectsAllNamesWithPrefix() {
        fill();

        String[] found = trie.autocomplete("мар", Trie.NO_LIMIT);

        assertEquals(3, found.length);
    }

    @Test
    void autocompleteReturnsNamesInAlphabeticalOrder() {
        fill();

        String[] found = trie.autocomplete("мар", Trie.NO_LIMIT);

        assertEquals("марина", found[0]);
        assertEquals("мария", found[1]);
        assertEquals("марк", found[2]);
    }

    @Test
    void autocompleteRespectsLimit() {
        fill();

        String[] found = trie.autocomplete("на", 1);

        assertEquals(1, found.length);
    }

    @Test
    void autocompleteOfEmptyPrefixReturnsEverything() {
        fill();

        assertEquals(6, trie.autocomplete("", Trie.NO_LIMIT).length);
    }

    @Test
    void autocompleteOfUnknownPrefixReturnsEmptyArray() {
        fill();

        assertEquals(0, trie.autocomplete("яяя", Trie.NO_LIMIT).length);
    }

    @Test
    void autocompleteIncludesExactMatch() {
        trie.insert("иван");
        trie.insert("иванова");

        String[] found = trie.autocomplete("иван", Trie.NO_LIMIT);

        assertEquals(2, found.length);
    }

    @Test
    void autocompleteStopsAtFirstDivergingBranch() {
        fill();

        String[] found = trie.autocomplete("марк", Trie.NO_LIMIT);

        assertEquals(1, found.length);
        assertEquals("марк", found[0]);
    }

    @Test
    void countByPrefixCountsContactsInSubtree() {
        fill();

        assertEquals(3, trie.countByPrefix("мар"));
    }

    @Test
    void countByPrefixIsZeroForUnknownPrefix() {
        fill();

        assertEquals(0, trie.countByPrefix("яяя"));
    }

    @Test
    void countByPrefixOfEmptyPrefixCountsEverything() {
        fill();

        assertEquals(6, trie.countByPrefix(""));
    }

    @Test
    void countByPrefixDecreasesAfterRemoval() {
        fill();

        trie.removeKey("мария");

        assertEquals(2, trie.countByPrefix("мар"));
    }

    @Test
    void countByPrefixCountsDuplicateNamesSeparately() {
        trie.insert("иван");
        trie.insert("иван");

        assertEquals(2, trie.countByPrefix("иван"));
    }
}