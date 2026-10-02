package ru.litper.structure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Базовые операции префиксного дерева: вставка, поиск, удаление.
 * Каждый тест проверяет одно поведение.
 */
class TrieTest {

    private final Trie<String> trie = new Trie<>(String.class, value -> value);

    @Test
    void insertAddsValue() {
        trie.insert("Иван");

        assertEquals(1, trie.size());
    }

    @Test
    void insertSharesPrefixInSingleNode() {
        trie.insert("мария");
        trie.insert("марина");

        // корень + общий префикс «мари» + различающиеся символы: м-а-р-и-я / м-а-р-и-н-а
        assertEquals(8, trie.nodeCount());
    }

    @Test
    void findAllReturnsValueByKey() {
        trie.insert("Иван");

        String[] found = trie.findAll("Иван");

        assertEquals(1, found.length);
        assertEquals("Иван", found[0]);
    }

    @Test
    void findAllReturnsEmptyArrayForUnknownKey() {
        trie.insert("Иван");

        String[] found = trie.findAll("Пётр");

        assertEquals(0, found.length);
    }

    @Test
    void equalKeysAccumulateInOneTerminalNode() {
        trie.insert("Иван");
        trie.insert("Иван");

        assertEquals(2, trie.findAll("Иван").length);
        assertEquals(2, trie.size());
    }

    @Test
    void findNodeReturnsNullForUnknownKey() {
        trie.insert("Иван");

        assertNull(trie.findNode("Пётр"));
    }

    @Test
    void findNodeReturnsNodeForKnownKey() {
        trie.insert("Иван");

        assertNotNull(trie.findNode("Иван"));
    }

    @Test
    void containsReportsPresenceOfKey() {
        trie.insert("Иван");

        assertTrue(trie.contains("Иван"));
    }

    @Test
    void removeDeletesValue() {
        String value = "Иван";
        trie.insert(value);

        boolean removed = trie.remove(value);

        assertTrue(removed);
        assertEquals(0, trie.size());
    }

    @Test
    void removeReturnsFalseForAbsentValue() {
        trie.insert("Иван");

        assertFalse(trie.remove("Пётр"));
    }

    @Test
    void removeDeletesAllValuesWithSameKey() {
        trie.insert("Иван");
        trie.insert("Иван");
        trie.insert("Мария");

        trie.removeKey("Иван");

        assertEquals(0, trie.findAll("Иван").length);
        assertEquals(1, trie.size());
    }

    @Test
    void removeKeyRemovesEmptyNodesFromTree() {
        trie.insert("АБВ");
        int nodesWithWord = trie.nodeCount();

        trie.removeKey("АБВ");

        assertEquals(1, trie.nodeCount());
        assertTrue(nodesWithWord > 1);
    }

    @Test
    void removePrefixDeletesWholeSubtree() {
        trie.insert("Иван");
        trie.insert("Иванова");
        trie.insert("Пётр");

        int removed = trie.removePrefix("Иван");

        assertEquals(2, removed);
        assertEquals(1, trie.size());
    }

    @Test
    void removePrefixRejectsEmptyPrefix() {
        assertThrows(IllegalArgumentException.class, () -> trie.removePrefix(""));
    }

    @Test
    void removePrefixOfUnknownPrefixRemovesNothing() {
        trie.insert("Иван");

        assertEquals(0, trie.removePrefix("Ян"));
        assertEquals(1, trie.size());
    }

    @Test
    void clearRemovesEverything() {
        trie.insert("Иван");
        trie.insert("Пётр");

        trie.clear();

        assertEquals(0, trie.size());
        assertEquals(1, trie.nodeCount());
        assertTrue(trie.isEmpty());
    }

    @Test
    void toArrayReturnsValuesInAlphabeticalOrder() {
        trie.insert("Пётр");
        trie.insert("Анна");
        trie.insert("Мария");

        String[] all = trie.toArray();

        assertEquals(3, all.length);
        assertEquals("Анна", all[0]);
        assertEquals("Мария", all[1]);
        assertEquals("Пётр", all[2]);
    }

    @Test
    void insertRejectsNullValue() {
        assertThrows(IllegalArgumentException.class, () -> trie.insert((String) null));
    }

    @Test
    void insertRejectsEmptyKey() {
        assertFalse(trie.insert(""));
    }

    @Test
    void nodeExposesDepth() {
        trie.insert("Иван");

        TrieNode<String> node = trie.findNode("Ива");

        assertNotNull(node);
        assertEquals(3, node.depth());
    }

    @Test
    void subtreeCountIncludesDescendantNames() {
        trie.insert("Иван");

        assertEquals(1, trie.findNode("Ива").subtreeCount());
    }

    @Test
    void terminalNodeCountsValuesInSubtree() {
        trie.insert("Иван");

        assertEquals(1, trie.findNode("Иван").subtreeCount());
    }
}