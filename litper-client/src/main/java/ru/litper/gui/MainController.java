package ru.litper.gui;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
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
import ru.litper.client.dto.ImportResultDto;
import ru.litper.client.service.RemoteContactService;
import ru.litper.model.Contact;
import ru.litper.model.CorporateContact;
import ru.litper.model.Editable;
import ru.litper.model.EmergencyContact;
import ru.litper.persistence.serialize.SchemaVersion;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

public class MainController {

    private static final FileChooser.ExtensionFilter CSV_FILTER =
            new FileChooser.ExtensionFilter("CSV-файл", "*.csv");
    private static final FileChooser.ExtensionFilter JSON_FILTER =
            new FileChooser.ExtensionFilter("JSON-файл", "*.json");
    private static final FileChooser.ExtensionFilter BINARY_FILTER =
            new FileChooser.ExtensionFilter("Бинарный файл", "*.bin");

    private static final int SUGGESTION_LIMIT = 50;
    private static final int DEFAULT_GENERATE_COUNT = 1000;
    private static final long GENERATE_SEED = 42L;

    private final RemoteContactService service;
    private final String baseUrl;
    private final TaskRunner taskRunner = new TaskRunner();

    private final BorderPane root = new BorderPane();
    private final TableView<Contact> table = new TableView<>();
    private final TriePanel triePanel;
    private final Label statusLabel = new Label(" ");

    private final Button loadButton = new Button("Загрузить CSV");
    private final Button saveButton = new Button("Сохранить CSV");
    private final Button refreshButton = new Button("Обновить");
    private final Button exportButton = new Button("Экспорт");
    private final Button importButton = new Button("Импорт");
    private final Button addButton = new Button("Добавить");
    private final Button editButton = new Button("Изменить");
    private final Button generateButton = new Button("Сгенерировать");
    private final Button clearButton = new Button("Очистить");

    public MainController(RemoteContactService service, String baseUrl) {
        this.service = service;
        this.baseUrl = baseUrl;
        this.triePanel = new TriePanel(buildTrieActions());

        root.setTop(buildToolbar());
        root.setCenter(buildCenter());

        table.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldValue, newValue) -> {
                    updateEditButton(newValue);
                    if (newValue != null) {
                        triePanel.setPrefix(newValue.getName());
                        onLookup(newValue.getName());
                    }
                });
        updateEditButton(null);
        service.addChangeListener(() -> Platform.runLater(this::refreshAll));
        refreshAll();
        setStatus("REST API: " + baseUrl + "/api/contacts");
    }

    public Region getView() {
        return root;
    }

    public void reload() {
        onRefresh();
    }

    private HBox buildToolbar() {
        addButton.setTooltip(new Tooltip("Добавить можно только редактируемый тип (корпоративный)"));
        generateButton.setTooltip(new Tooltip("Сгенерировать набор данных для дерева"));
        clearButton.setTooltip(new Tooltip("Полностью очистить дерево"));
        refreshButton.setTooltip(new Tooltip("Перечитать данные с REST-сервера"));
        exportButton.setTooltip(new Tooltip("Экспорт в JSON или бинарный формат с выбором версии схемы"));
        importButton.setTooltip(new Tooltip("Импорт из JSON или бинарного формата, старые версии мигрируются"));

        loadButton.setOnAction(e -> onLoadCsv());
        saveButton.setOnAction(e -> onSaveCsv());
        refreshButton.setOnAction(e -> onRefresh());
        exportButton.setOnAction(e -> onExport());
        importButton.setOnAction(e -> onImport());
        addButton.setOnAction(e -> onAddContact());
        editButton.setOnAction(e -> onEditContact());
        generateButton.setOnAction(e -> onGenerate());
        clearButton.setOnAction(e -> onClear());

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox toolbar = new HBox(8, loadButton, saveButton, refreshButton, exportButton, importButton,
                addButton, editButton, generateButton, clearButton, spacer,
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
        table.getColumns().add(column("Заметка", Contact::getNote));
        table.getColumns().add(column("Теги", Contact::getTags));
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

    private void onLoadCsv() {
        File file = chooseFile("Загрузить CSV", false);
        if (file == null) {
            return;
        }
        Task<ImportResultDto> task = new Task<>() {
            @Override
            protected ImportResultDto call() throws Exception {
                return service.importCsv(Files.readAllBytes(file.toPath()));
            }
        };
        runInBackground(task, "Ошибка загрузки CSV", () -> {
            refreshAll();
            ImportResultDto result = task.getValue();
            setStatus("Загружено " + result.contacts()
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
                byte[] data = service.exportCsv();
                Files.write(file.toPath(), data);
                return service.size();
            }
        };
        runInBackground(task, "Ошибка сохранения CSV", () ->
                setStatus("Сохранено контактов: " + task.getValue()));
    }

    private void onRefresh() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                service.refresh();
                return null;
            }
        };
        runInBackground(task, "Ошибка обновления", () -> {
            refreshAll();
            setStatus("Данные обновлены. REST API: " + baseUrl + "/api/contacts");
        });
    }

    private void onExport() {
        FileChooser chooser = buildDataChooser("Экспорт справочника", JSON_FILTER, BINARY_FILTER);
        File file = chooser.showSaveDialog(root.getScene().getWindow());
        if (file == null) {
            return;
        }
        String format = chooser.getSelectedExtensionFilter() == BINARY_FILTER ? "bin" : "json";
        SchemaVersion version = chooseSchemaVersion();
        if (version == null) {
            return;
        }
        if (version != SchemaVersion.CURRENT
                && !confirm("При экспорте в схему " + version.number()
                        + " будут потеряны " + version.lostFields() + ". Продолжить?")) {
            return;
        }

        Path target = withExtension(file.toPath(), format);
        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                byte[] data = service.exportData(format, version.number());
                Files.write(target, data);
                return service.size();
            }
        };
        runInBackground(task, "Ошибка экспорта", () ->
                setStatus("Экспортировано в " + format + " (схема " + version.number()
                        + "): " + task.getValue() + " контактов (" + target.getFileName() + ")"));
    }

    private void onImport() {
        FileChooser chooser = buildDataChooser("Импорт справочника", JSON_FILTER, BINARY_FILTER);
        File file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file == null) {
            return;
        }
        String fileName = file.getName().toLowerCase(Locale.ROOT);
        String format = fileName.endsWith(".bin") ? "bin" : "json";

        Task<ImportResultDto> task = new Task<>() {
            @Override
            protected ImportResultDto call() throws Exception {
                return service.importData(format, Files.readAllBytes(file.toPath()));
            }
        };
        runInBackground(task, "Ошибка импорта", () -> {
            triePanel.setPrefix("");
            triePanel.setSuggestions(new String[0]);
            refreshAll();
            ImportResultDto result = task.getValue();
            String suffix = result.migrated()
                    ? ", миграция " + result.sourceVersion() + " → " + result.currentVersion()
                    : "";
            setStatus("Импортировано из " + result.format() + ": "
                    + result.contacts() + " контактов" + suffix);
        });
    }

    private SchemaVersion chooseSchemaVersion() {
        ChoiceDialog<SchemaVersion> dialog = new ChoiceDialog<>(
                SchemaVersion.CURRENT, SchemaVersion.values());
        dialog.initOwner(root.getScene().getWindow());
        dialog.setTitle("Версия схемы");
        dialog.setHeaderText("В какую версию схемы сохранить файл?");
        dialog.setContentText("Версия:");
        return dialog.showAndWait().orElse(null);
    }

    private static FileChooser buildDataChooser(String title, FileChooser.ExtensionFilter... filters) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().addAll(filters);
        return chooser;
    }

    private static Path withExtension(Path file, String extension) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith("." + extension)) {
            return file;
        }
        return file.resolveSibling(file.getFileName() + "." + extension);
    }

    private void onAddContact() {
        ContactDialog.showAdd(root.getScene().getWindow()).ifPresent(contact -> {
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() {
                    service.add(contact);
                    return null;
                }
            };
            runInBackground(task, "Ошибка добавления контакта", () -> {
                refreshAll();
                setStatus("Добавлен контакт: " + contact.getName());
            });
        });
    }

    private void onEditContact() {
        Contact selected = table.getSelectionModel().getSelectedItem();
        if (!(selected instanceof CorporateContact corporate)) {
            setStatus("Выбранный контакт нельзя редактировать (read-only тип)");
            return;
        }
        ContactDialog.showEdit(root.getScene().getWindow(), corporate).ifPresent(updated -> {
            Task<Boolean> task = new Task<>() {
                @Override
                protected Boolean call() {
                    return service.update(selected, updated);
                }
            };
            runInBackground(task, "Ошибка изменения контакта", () -> {
                if (Boolean.TRUE.equals(task.getValue())) {
                    triePanel.setPrefix(updated.getName());
                    refreshAll();
                    setStatus("Изменён контакт: " + updated.getName());
                } else {
                    showError("Ошибка изменения контакта",
                            new IllegalStateException("Контакт не найден в справочнике"));
                }
            });
        });
    }

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
                return service.generate(count, GENERATE_SEED);
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
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                service.clear();
                return null;
            }
        };
        runInBackground(task, "Ошибка очистки", () -> {
            triePanel.setSuggestions(new String[0]);
            triePanel.setPrefix("");
            refreshAll();
            setStatus("Дерево очищено");
        });
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
        if (service.findByName(name).length == 0) {
            setStatus("Контакт с именем «" + name + "» не найден");
            return;
        }
        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() {
                return service.removeByName(name);
            }
        };
        runInBackground(task, "Ошибка удаления", () -> {
            refreshAll();
            setStatus("Удалено контактов: " + task.getValue());
        });
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
        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() {
                return service.removeByPrefix(prefix);
            }
        };
        runInBackground(task, "Ошибка удаления", () -> {
            refreshAll();
            setStatus("Удалено по префиксу «" + prefix + "»: " + task.getValue());
        });
    }

    private void refreshAll() {
        refreshTable();
        updateTrieView();
    }

    private void refreshTable() {
        table.getItems().setAll(service.getAll());
    }

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
        refreshButton.setDisable(busy);
        exportButton.setDisable(busy);
        importButton.setDisable(busy);
        addButton.setDisable(busy);
        editButton.setDisable(busy || !(table.getSelectionModel().getSelectedItem() instanceof Editable));
        generateButton.setDisable(busy);
        clearButton.setDisable(busy);
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }

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
