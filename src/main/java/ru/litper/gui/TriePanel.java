package ru.litper.gui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import ru.litper.model.Contact;
import ru.litper.structure.Trie;
import ru.litper.structure.TrieNode;

import java.util.Arrays;
import java.util.Locale;

/**
 * Панель визуализации префиксного дерева (требование лабораторной №2).
 *
 * <p>Рисует внутреннее устройство структуры на {@link Canvas}: y — глубина узла,
 * x — позиция листа, поэтому общий вид совпадает с картинкой из методички.
 * После каждой операции дерево перерисовывается полностью, а узлы, посещённые
 * последней операцией (из {@link Trie#lastTrace()}), подсвечиваются.</p>
 *
 * <p>Панель только показывает данные и сообщает о намерениях пользователя
 * через {@link TrieActions}; сами операции выполняет {@link MainController}
 * над хранилищем.</p>
 */
public class TriePanel extends VBox {

    /** Действия пользователя, которые панель передаёт контроллеру. */
    public interface TrieActions {

        /** Изменился префикс: нужен список подсказок и перерисовка. */
        void onLookup(String prefix);

        /** Найти контакт по точному имени и подсветить путь. */
        void onFindExact(String name);

        /** Удалить контакт по имени. */
        void onRemoveByName(String name);

        /** Удалить всё поддерево по префиксу. */
        void onRemoveByPrefix(String prefix);
    }

    /** Больше узлов рисовать бессмысленно: 10^5 контактов в одно окно не влезут. */
    private static final int MAX_DRAWN_NODES = 400;
    private static final int INITIAL_LAYOUT_CAPACITY = 64;

    private static final double H_GAP = 34;
    private static final double V_GAP = 66;
    private static final double MARGIN = 44;
    private static final double NODE_RADIUS = 15;
    private static final double MIN_CANVAS_WIDTH = 260;
    private static final double MIN_CANVAS_HEIGHT = 200;

    private static final Color EDGE_COLOR = Color.web("#bdc1c6");
    private static final Color NODE_FILL = Color.web("#ffffff");
    private static final Color NODE_STROKE = Color.web("#5f6368");
    private static final Color TERMINAL_FILL = Color.web("#e8f0fe");
    private static final Color TERMINAL_STROKE = Color.web("#1a73e8");
    private static final Color VISITED_FILL = Color.web("#fce8b2");
    private static final Color VISITED_STROKE = Color.web("#e8710a");
    private static final Color TEXT_COLOR = Color.web("#202124");

    private final TrieActions actions;

    private final TextField prefixField = new TextField();
    private final ListView<String> suggestionList = new ListView<>();
    private final Canvas canvas = new Canvas(MIN_CANVAS_WIDTH, MIN_CANVAS_HEIGHT);
    private final ScrollPane canvasScroll = new ScrollPane(canvas);
    private final Label statsLabel = new Label(" ");
    private final Label operationLabel = new Label(" ");

    /** Раскладка узлов на канвасе (собственный массив, не список). */
    private NodeView[] views = new NodeView[INITIAL_LAYOUT_CAPACITY];
    private int viewCount;
    private double nextX;
    private boolean truncated;

    /** Узлы, посещённые последней операцией, — их подсвечиваем. */
    private TrieNode<Contact>[] visited = emptyVisited();

    @SuppressWarnings("unchecked")
    private static TrieNode<Contact>[] emptyVisited() {
        return (TrieNode<Contact>[]) new TrieNode<?>[0];
    }

    public TriePanel(TrieActions actions) {
        this.actions = actions;
        setSpacing(6);
        setPadding(new Insets(10));
        getChildren().addAll(
                buildToolbar(),
                operationLabel,
                buildCanvas(),
                buildSuggestions(),
                statsLabel,
                buildLegend());
    }

    // ------------------------------------------------------------------ построение интерфейса

    private HBox buildToolbar() {
        Button findButton = new Button("Найти");
        Button removeNameButton = new Button("Удалить по имени");
        Button removePrefixButton = new Button("Удалить по префиксу");
        removePrefixButton.setTooltip(new Tooltip(
                "Удаляет все контакты, имена которых начинаются с префикса"));

        findButton.setOnAction(e -> actions.onFindExact(prefixField.getText()));
        removeNameButton.setOnAction(e -> actions.onRemoveByName(prefixField.getText()));
        removePrefixButton.setOnAction(e -> actions.onRemoveByPrefix(prefixField.getText()));

        prefixField.setPromptText("Префикс имени, например «ив»");
        HBox.setHgrow(prefixField, Priority.ALWAYS);
        prefixField.textProperty().addListener((obs, oldValue, newValue) ->
                actions.onLookup(newValue == null ? "" : newValue));

        HBox toolbar = new HBox(6,
                new Label("Префикс:"), prefixField, findButton, removeNameButton, removePrefixButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        return toolbar;
    }

    private ScrollPane buildCanvas() {
        canvasScroll.setContent(canvas);
        canvasScroll.setPannable(true);
        canvasScroll.setPrefHeight(360);
        VBox.setVgrow(canvasScroll, Priority.ALWAYS);
        return canvasScroll;
    }

    private VBox buildSuggestions() {
        suggestionList.setPrefHeight(110);
        suggestionList.setOnMouseClicked(e -> {
            String selected = suggestionList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                prefixField.setText(selected);
                actions.onFindExact(selected);
            }
        });
        VBox box = new VBox(4, new Label("Автодополнение по префиксу:"), suggestionList);
        box.setSpacing(4);
        return box;
    }

    private HBox buildLegend() {
        return new HBox(12,
                legendItem(NODE_FILL, NODE_STROKE, "узел"),
                legendItem(TERMINAL_FILL, TERMINAL_STROKE, "конец имени"),
                legendItem(VISITED_FILL, VISITED_STROKE, "посещён последней операцией"));
    }

    private static HBox legendItem(Color fill, Color stroke, String text) {
        Region swatch = new Region();
        swatch.setPrefSize(14, 14);
        swatch.setStyle("-fx-background-color: " + toHex(fill)
                + "; -fx-border-color: " + toHex(stroke)
                + "; -fx-border-radius: 7; -fx-border-width: 2;");
        HBox item = new HBox(4, swatch, new Label(text));
        item.setAlignment(Pos.CENTER_LEFT);
        return item;
    }

    private static String toHex(Color color) {
        return String.format("#%02x%02x%02x",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }

    // ------------------------------------------------------------------ обновление извне

    /** Текущий префикс из поля ввода (пустая строка — показываем всё дерево). */
    public String getPrefix() {
        return prefixField.getText() == null ? "" : prefixField.getText();
    }

    public void setPrefix(String prefix) {
        prefixField.setText(prefix);
    }

    /** Показывает подсказки автодополнения. */
    public void setSuggestions(String[] names) {
        suggestionList.getItems().setAll(names);
    }

    /**
     * Полностью перерисовывает панель по текущему состоянию дерева.
     *
     * @param trie             структура для отрисовки
     * @param focusPrefix      поддерево для показа (пусто — всё дерево);
     *                         префикс нормализуется так же, как в {@code ContactService}
     * @param contactsInSubtree сколько контактов в показываемом поддереве
     */
    public void update(Trie<Contact> trie, String focusPrefix, int contactsInSubtree) {
        // Трассировку снимаем до поиска узла: findNode сам меняет «последнюю операцию».
        visited = trie.lastTrace();
        operationLabel.setText("Последняя операция: " + trie.lastOperation());

        TrieNode<Contact> focus = resolveFocus(trie, focusPrefix);
        layout(focus);
        paint(trie, focus, focusPrefix, contactsInSubtree);
    }

    private TrieNode<Contact> resolveFocus(Trie<Contact> trie, String focusPrefix) {
        String prefix = focusPrefix == null ? "" : focusPrefix.trim().toLowerCase(Locale.ROOT);
        if (prefix.isEmpty()) {
            return trie.root();
        }
        TrieNode<Contact> node = trie.findNode(prefix);
        return node == null ? trie.root() : node;
    }

    // ------------------------------------------------------------------ раскладка

    private void layout(TrieNode<Contact> focus) {
        views = new NodeView[INITIAL_LAYOUT_CAPACITY];
        viewCount = 0;
        nextX = MARGIN;
        truncated = false;
        placeNode(focus, 0, -1);
    }

    /** Обход: место листа резервируется слева направо, узел встаёт по центру своих детей. */
    private double placeNode(TrieNode<Contact> node, int depth, int parentIndex) {
        if (viewCount >= MAX_DRAWN_NODES) {
            truncated = true;
            return nextX;
        }
        if (viewCount == views.length) {
            views = Arrays.copyOf(views, views.length * 2);
        }
        NodeView view = new NodeView(node, parentIndex);
        view.depth = depth;
        views[viewCount++] = view;

        if (node.childCount() == 0 || viewCount >= MAX_DRAWN_NODES) {
            view.x = nextX;
            nextX += H_GAP;
            return view.x;
        }
        double firstChildX = 0;
        double lastChildX = 0;
        for (int i = 0; i < node.childCount() && viewCount < MAX_DRAWN_NODES; i++) {
            double childX = placeNode(node.child(i), depth + 1, viewCount - 1);
            if (i == 0) {
                firstChildX = childX;
            }
            lastChildX = childX;
        }
        view.x = (firstChildX + lastChildX) / 2;
        return view.x;
    }

    // ------------------------------------------------------------------ отрисовка

    private void paint(Trie<Contact> trie, TrieNode<Contact> focus,
                       String focusPrefix, int contactsInSubtree) {
        int maxDepth = 0;
        for (int i = 0; i < viewCount; i++) {
            maxDepth = Math.max(maxDepth, views[i].depth);
        }
        double width = Math.max(nextX + MARGIN, MIN_CANVAS_WIDTH);
        double height = Math.max((maxDepth + 1) * V_GAP + MARGIN, MIN_CANVAS_HEIGHT);

        canvas.setWidth(width);
        canvas.setHeight(height);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, width, height);

        if (trie.isEmpty()) {
            g.setFill(TEXT_COLOR);
            g.fillText("Дерево пусто — добавьте, загрузите или сгенерируйте контакты", MARGIN, MARGIN);
            updateStats(trie.nodeCount(), trie.size(), contactsInSubtree, focusPrefix, focus);
            return;
        }
        drawEdges(g);
        for (int i = 0; i < viewCount; i++) {
            drawNode(g, views[i]);
        }
        updateStats(trie.nodeCount(), trie.size(), contactsInSubtree, focusPrefix, focus);
    }

    private void drawEdges(GraphicsContext g) {
        g.setStroke(EDGE_COLOR);
        g.setLineWidth(1.4);
        for (int i = 0; i < viewCount; i++) {
            NodeView view = views[i];
            if (view.parentIndex < 0) {
                continue;
            }
            NodeView parent = views[view.parentIndex];
            g.strokeLine(parent.x, yOf(parent), view.x, yOf(view));
        }
    }

    private void drawNode(GraphicsContext g, NodeView view) {
        TrieNode<Contact> node = view.node;
        boolean isVisited = isVisited(node);

        Color fill = isVisited ? VISITED_FILL
                : node.isTerminal() ? TERMINAL_FILL : NODE_FILL;
        Color stroke = isVisited ? VISITED_STROKE
                : node.isTerminal() ? TERMINAL_STROKE : NODE_STROKE;

        g.setFill(fill);
        g.setStroke(stroke);
        g.setLineWidth(isVisited || node.isTerminal() ? 2.5 : 1.4);
        g.fillOval(view.x - NODE_RADIUS, yOf(view) - NODE_RADIUS, NODE_RADIUS * 2, NODE_RADIUS * 2);
        g.strokeOval(view.x - NODE_RADIUS, yOf(view) - NODE_RADIUS, NODE_RADIUS * 2, NODE_RADIUS * 2);

        Font font = Font.font(12);
        g.setFont(font);
        g.setFill(TEXT_COLOR);
        String symbol = node.isRoot() ? "root" : String.valueOf(node.symbol());
        g.fillText(symbol, view.x - font.getSize() * symbol.length() / 3.2, yOf(view) + 4);

        if (node.terminalCount() > 1) {
            g.setFill(TERMINAL_STROKE);
            g.fillText("x" + node.terminalCount(),
                    view.x + NODE_RADIUS - 6, yOf(view) - NODE_RADIUS + 10);
        }
    }

    private boolean isVisited(TrieNode<Contact> node) {
        for (TrieNode<Contact> visitedNode : visited) {
            if (visitedNode == node) {
                return true;
            }
        }
        return false;
    }

    private static double yOf(NodeView view) {
        return MARGIN + view.depth * V_GAP;
    }

    // ------------------------------------------------------------------ подписи

    private void updateStats(int nodeCount, int size, int inSubtree,
                             String prefix, TrieNode<Contact> focus) {
        String shownPrefix = prefix == null || prefix.isBlank()
                ? "всё дерево"
                : "«" + prefix.trim() + "»";
        StringBuilder text = new StringBuilder();
        text.append("Контактов: ").append(size)
                .append(" | узлов: ").append(nodeCount)
                .append(" | в поддереве ").append(shownPrefix).append(": ").append(inSubtree);
        if (truncated) {
            text.append(" | показаны первые ").append(MAX_DRAWN_NODES).append(" узлов");
        }
        if (focus != null && focus.isTerminal()) {
            text.append(" | префикс совпадает с именем: контактов ").append(focus.terminalCount());
        }
        statsLabel.setText(text.toString());
    }

    /** Узел на канвасе: позиция, глубина и ссылка на родителя. */
    private static final class NodeView {

        private final TrieNode<Contact> node;
        private final int parentIndex;
        private int depth;
        private double x;

        private NodeView(TrieNode<Contact> node, int parentIndex) {
            this.node = node;
            this.parentIndex = parentIndex;
        }
    }
}