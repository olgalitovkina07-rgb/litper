package ru.litper.structure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Трассировка обхода — она нужна визуализации, чтобы подсветить
 * посещённые при операции узлы.
 */
class TrieTraceTest {

    private final Trie<String> trie = new Trie<>(String.class, value -> value);

    @Test
    void insertTraceContainsNodesOfInsertedKey() {
        trie.insert("Иван");

        TrieNode<String>[] trace = trie.lastTrace();

        // корень + 4 символа
        assertEquals(5, trace.length);
    }

    @Test
    void insertTraceEndsWithTerminalNode() {
        trie.insert("Иван");

        TrieNode<String>[] trace = trie.lastTrace();
        TrieNode<String> last = trace[trace.length - 1];

        assertTrue(last.isTerminal());
    }

    @Test
    void searchTraceContainsNodesOfSearchedKey() {
        trie.insert("Иван");

        trie.findAll("Ива");

        assertEquals(4, trie.lastTrace().length);
    }

    @Test
    void searchTraceStopsAtFirstMissingChild() {
        trie.insert("Иван");

        trie.findAll("Иваа");

        TrieNode<String>[] trace = trie.lastTrace();

        // корень + и, в, а — дальше символа «а» в узле «а» нет
        assertEquals(4, trace.length);
    }

    @Test
    void traceIsClearedBeforeNextOperation() {
        trie.insert("Иван");

        trie.countByPrefix("Ив");

        assertEquals(3, trie.lastTrace().length);
    }

    @Test
    void traceNodesBelongToTree() {
        trie.insert("Иван");

        TrieNode<String>[] trace = trie.lastTrace();

        assertSame(trie.root(), trace[0]);
    }

    @Test
    void lastOperationDescribesLastCall() {
        trie.insert("Иван");

        trie.countByPrefix("Ив");

        assertEquals("подсчёт по префиксу: Ив", trie.lastOperation());
    }
}