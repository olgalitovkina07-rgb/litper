package ru.litper.gui;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import ru.litper.model.Contact;
import ru.litper.model.CorporateContact;
import ru.litper.model.Editable;
import ru.litper.model.EmergencyContact;
import ru.litper.persistence.CsvLoadResult;
import ru.litper.persistence.CsvLoader;
import ru.litper.persistence.CsvWriter;
import ru.litper.service.ContactGenerator;
import ru.litper.service.ContactService;

import java.io.File;
import java.util.Optional;
import java.util.function.Function;

/**
 * Контроллер GUI: собирает окно и связывает кнопки с хранилищем.
 *
 * <p>Логика представления — здесь; хранилище — {@link ContactService}
 * (префиксное дерево), чтение/запись CSV — {@link CsvLoader}/{@link CsvWriter},
 * рисование структуры — {@link TriePanel}. Тяжёлые операции всегда идут через
 * {@link TaskRunner}, чтобы не блокировать FX-поток.</p>
 */
public class MainController {

    private static final FileChooser.ExtensionFilter CSV_FILTER =
            new FileChooser.ExtensionFilter("CSV-файл", "*.csv");

    /** Сколько подсказок показывать в панели автодополнения. */
    private static final int SUGGESTION_LIMIT = 50;
    private static final int DEFAULT_GENERATE_COUNT = 1000;
    private static final long GENERATE_SEED = 42L;

    private final ContactService service;
    private final TaskRunner taskRunner = new TaskRunner();

    private final BorderPane root = new BorderPane();
    private final TableView<Contact> table = new TableView<>();
    private final TriePanel triePanel;
    private final Label statusLabel = new Label(" ");

    private final Button loadButton = new Button("Загрузить CSV");
    private final Button saveButton = new Button("Сохранить CSV");
    private final Button addButton = new Button("Добавить");
    private final Button editButton = new Button("Изменить");
    private final Button generateButton = new Button("Сгенерировать");
    private final Button clearButton = new Button("Очистить");

    public MainController(ContactService service) {
        this.service = service;
        this.triePanel = new TriePanel(buildTrieActions());

        root.setTop(buildToolbar());
        root.setCenter(buildCenter());

        table.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldValue, newValue) -> {
                    updateEditButton(newValue);
                    if (newValue != null) {
                        // Выбор строки в таблице сразу показывает путь к этому имени в дереве.
                        triePanel.setPrefix(newValue.getName());
                        onLookup(newValue.getName());
                    }
                });
        updateEditButton(null);
        refreshAll();
    }

    /** Корневая панель окна (для сцены). */
    public Region getView() {
        return root;
    }

    // ------------------------------------------------------------------ интерфейс

    private HBox buildToolbar() {
        addButton.setTooltip(new Tooltip("Добавить можно только редактируемый тип (корпоративный)"));
        generateButton.setTooltip(new Tooltip("Сгенерировать набор данных для дерева"));
        clearButton.setTooltip(new Tooltip("Полностью очистить дерево"));

        loadButton.setOnAction(e -> onLoadCsv());
        saveButton.setOnAction(e -> onSaveCsv());
        addButton.setOnAction(e -> onAddContact());
        editButton.setOnAction(e -> onEditContact());
        generateButton.setOnAction(e -> onGenerate());
        clearButton.setOnAction(e -> onClear());

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox toolbar = new HBox(8, loadButton, saveButton, addButton, editButton,
                generateButton, clearButton, spacer,
                taskRunner.getProgressBar(), taskRunner.getCancelButton());
        toolbar.setPadding(new Insets(10));
        toolbar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return toolbar;
    }

    private SplitPane buildCenter() {
        table.getColumns().add(column("Тип", MainController::typeOf));
        table.getColumns().add(column("Имя", Contact::getName));
        table.getColumns().add(column("Телефон", Contact::getPhone));
        table.getColumns().add(column("E-mail", Contact::getEmail));
        table.getColumns().add(column("Организация", Contact::getOrganization));
        table.getColumns().add(column("Должность", MainController::positionOf));
        table.getColumns().add(column("Внутр. номер", MainController::internalNumberOf));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Контактов нет. Загрузите CSV, сгенерируйте или добавьте контакт."));

        statusLabel.setPadding(new Insets(6, 10, 6, 10));
        VBox left = new VBox(table, statusLabel);
        VBox.setVgrow(table, Priority.ALWAYS);

        SplitPane split = new SplitPane(left, triePanel);
        split.setDividerPositions(0.45);
        return split;
    }

    private static String typeOf(Contact contact) {
        return contact instanceof EmergencyContact ? "Аварийный" : "Корпоративный";
    }

    private static String positionOf(Contact contact) {
        return contact instanceof CorporateContact corporate ? corporate.getPosition() : "";
    }

    private static String internalNumberOf(Contact contact) {
        return contact instanceof CorporateContact corporate ? corporate.getInternalNumber() : "";
    }

    private static <T> TableColumn<T, String> column(String title, Function<T, String> extractor) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(extractor.apply(data.getValue())));
        return column;
    }

    private TriePanel.TrieActions buildTrieActions() {
        return new TriePanel.TrieActions() {
            @Override
            public void onLookup(String prefix) {
                MainController.this.onLookup(prefix);
            }

            @Override
            public void onFindExact(String name) {
                MainController.this.onFindExact(name);
            }

            @Override
            public void onRemoveByName(String name) {
                MainController.this.onRemoveByName(name);
            }

            @Override
            public void onRemoveByPrefix(String prefix) {
                MainController.this.onRemoveByPrefix(prefix);
            }
        };
    }

    // ------------------------------------------------------------------ работа с данными (лаб. 1)

    private void onLoadCsv() {
        File file = chooseFile("Загрузить CSV", false);
        if (file == null) {
            return;
        }
        CsvLoader loader = new CsvLoader();
        Task<CsvLoadResult> task = new Task<>() {
            @Override
            protected CsvLoadResult call() throws Exception {
                long totalLines = loader.countLines(file.toPath());
                return loader.load(file.toPath(), processed -> updateProgress(processed, totalLines));
            }
        };
        runInBackground(task, "Ошибка загрузки CSV", () -> {
            CsvLoadResult result = task.getValue();
            service.replaceAll(result.contacts());
            refreshAll();
            setStatus("Загружено " + result.contacts().length
                    + ", пропущено битых строк: " + result.skippedLines());
        });
    }

    private void onSaveCsv() {
        File file = chooseFile("Сохранить CSV", true);
        if (file == null) {
            return;
        }
        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                new CsvWriter().write(file.toPath(), service.getAll());
                return service.size();
            }
        };
        runInBackground(task, "Ошибка сохранения CSV", () ->
                setStatus("Сохранено контактов: " + task.getValue()));
    }

    private void onAddContact() {
        ContactDialog.showAdd(root.getScene().getWindow()).ifPresent(contact -> {
            service.add(contact);
            refreshAll();
            setStatus("Добавлен контакт: " + contact.getName());
        });
    }

    private void onEditContact() {
        Contact selected = table.getSelectionModel().getSelectedItem();
        if (!(selected instanceof CorporateContact corporate)) {
            setStatus("Выбранный контакт нельзя редактировать (read-only тип)");
            return;
        }
        ContactDialog.showEdit(root.getScene().getWindow(), corporate).ifPresent(updated -> {
            if (service.update(selected, updated)) {
                triePanel.setPrefix(updated.getName());
                refreshAll();
                setStatus("Изменён контакт: " + updated.getName());
            } else {
                showError("Ошибка изменения контакта",
                        new IllegalStateException("Контакт не найден в справочнике"));
            }
        });
    }

    // ------------------------------------------------------------------ операции над деревом (лаб. 2)

    private void onGenerate() {
        Optional<String> answer = askGenerateCount();
        if (answer.isEmpty()) {
            return;
        }
        int count;
        try {
            count = Integer.parseInt(answer.get().trim());
        } catch (NumberFormatException e) {
            showWarning("Количество должно быть целым числом: " + e.getMessage());
            return;
        }
        if (count <= 0) {
            showWarning("Количество должно быть положительным числом");
            return;
        }
        if (service.size() > 0 && !confirm("Заменить текущие контакты (" + service.size()
                + ") сгенерированными (" + count + ")?")) {
            return;
        }

        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() {
                Contact[] generated = new ContactGenerator().generate(
                        count, GENERATE_SEED,
                        (done, total) -> {
                            updateProgress(done, total);
                            return !isCancelled(); // кооперативная отмена
                        });
                service.replaceAll(generated);
                return generated.length;
            }
        };
        runInBackground(task, "Ошибка генерации данных", () -> {
            refreshAll();
            setStatus("Сгенерировано контактов: " + task.getValue());
        });
    }

    private void onClear() {
        if (!confirm("Полностью очистить дерево?")) {
            return;
        }
        service.clear();
        triePanel.setSuggestions(new String[0]);
        triePanel.setPrefix("");
        refreshAll();
        setStatus("Дерево очищено");
    }

    private void onLookup(String prefix) {
        Contact[] found = service.suggest(prefix, SUGGESTION_LIMIT);
        String[] names = new String[found.length];
        for (int i = 0; i < found.length; i++) {
            names[i] = found[i].getName();
        }
        triePanel.setSuggestions(names);
        updateTrieView();
    }

    private void onFindExact(String name) {
        Contact[] found = service.findByName(name);
        if (found.length == 0) {
            setStatus("Контакт с именем «" + name + "» не найден");
            updateTrieView();
            return;
        }
        triePanel.setPrefix(found[0].getName());
        table.getSelectionModel().clearSelection();
        table.getSelectionModel().select(found[0]);
        table.scrollTo(found[0]);
        setStatus("По имени «" + name + "» найдено контактов: " + found.length);
    }

    private void onRemoveByName(String name) {
        if (name == null || name.isBlank()) {
            showWarning("Введите имя контакта");
            return;
        }
        Contact[] found = service.findByName(name);
        if (found.length == 0) {
            setStatus("Контакт с именем «" + name + "» не найден");
            return;
        }
        int removed = 0;
        for (Contact contact : found) {
            if (service.remove(contact)) {
                removed++;
            }
        }
        refreshAll();
        setStatus("Удалено контактов: " + removed);
    }

    private void onRemoveByPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            showWarning("Введите префикс для удаления");
            return;
        }
        int inSubtree = service.countByPrefix(prefix);
        if (inSubtree == 0) {
            setStatus("По префиксу «" + prefix + "» ничего не найдено");
            return;
        }
        if (!confirm("Удалить " + inSubtree + " контактов по префиксу «" + prefix + "»?")) {
            return;
        }
        int removed = service.removeByPrefix(prefix);
        refreshAll();
        setStatus("Удалено по префиксу «" + prefix + "»: " + removed);
    }

    // ------------------------------------------------------------------ обновление интерфейса

    private void refreshAll() {
        refreshTable();
        updateTrieView();
    }

    private void refreshTable() {
        table.getItems().setAll(service.getAll());
    }

    /** Пересчитывает статистику и перерисовывает дерево с учётом текущего префикса. */
    private void updateTrieView() {
        String prefix = triePanel.getPrefix();
        int inSubtree = prefix.isBlank() ? service.size() : service.countByPrefix(prefix);
        triePanel.update(service.index(), prefix, inSubtree);
    }

    private void updateEditButton(Contact selected) {
        editButton.setDisable(!(selected instanceof Editable));
    }

    private void setBusy(boolean busy) {
        loadButton.setDisable(busy);
        saveButton.setDisable(busy);
        addButton.setDisable(busy);
        editButton.setDisable(busy || !(table.getSelectionModel().getSelectedItem() instanceof Editable));
        generateButton.setDisable(busy);
        clearButton.setDisable(busy);
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }

    // ------------------------------------------------------------------ вспомогательное

    /** Любая тяжёлая операция — в фоне, интерфейс не блокируется. */
    private void runInBackground(Task<?> task, String errorTitle, Runnable onSucceeded) {
        setBusy(true);
        taskRunner.run(task,
                () -> setBusy(false),
                onSucceeded,
                error -> showError(errorTitle, error));
    }

    private File chooseFile(String title, boolean forSaving) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(CSV_FILTER);
        return forSaving
                ? chooser.showSaveDialog(root.getScene().getWindow())
                : chooser.showOpenDialog(root.getScene().getWindow());
    }

    private Optional<String> askGenerateCount() {
        TextInputDialog dialog = new TextInputDialog(String.valueOf(DEFAULT_GENERATE_COUNT));
        dialog.initOwner(root.getScene() == null ? null : root.getScene().getWindow());
        dialog.setTitle("Генерация данных");
        dialog.setHeaderText("Сколько контактов сгенерировать?");
        dialog.setContentText("Количество:");
        return dialog.showAndWait();
    }

    private boolean confirm(String question) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(root.getScene().getWindow());
        alert.setTitle("Подтверждение");
        alert.setHeaderText(question);
        alert.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        return alert.showAndWait().map(button -> button == ButtonType.OK).orElse(false);
    }

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.initOwner(root.getScene().getWindow());
        alert.setTitle("Некорректные данные");
        alert.setHeaderText(message);
        alert.showAndWait();
    }

    private void showError(String title, Throwable error) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(root.getScene().getWindow());
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(String.valueOf(error.getMessage()));
        alert.showAndWait();
    }
}