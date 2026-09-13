package ru.litper.gui;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import ru.litper.model.Contact;
import ru.litper.model.CorporateContact;
import ru.litper.model.Editable;
import ru.litper.model.EmergencyContact;
import ru.litper.persistence.CsvLoadResult;
import ru.litper.persistence.CsvLoader;
import ru.litper.persistence.CsvWriter;
import ru.litper.service.ContactService;

import java.io.File;
import java.util.function.Function;

/**
 * Контроллер GUI: строит окно и обрабатывает действия пользователя.
 *
 * <p>Содержит только логику представления. Хранение данных — в {@link ContactService},
 * чтение/запись CSV — в {@link CsvLoader}/{@link CsvWriter}.</p>
 */
public class MainController {

    private static final FileChooser.ExtensionFilter CSV_FILTER =
            new FileChooser.ExtensionFilter("CSV-файл", "*.csv");

    private final ContactService service;
    private final BorderPane root = new BorderPane();
    private final TableView<Contact> table = new TableView<>();
    private final Button editButton = new Button("Изменить");
    private final Label statusLabel = new Label();

    public MainController(ContactService service) {
        this.service = service;

        root.setCenter(buildTable());
        root.setBottom(buildBottomPanel());

        table.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldValue, newValue) -> updateEditButton(newValue));
        updateEditButton(null);
        refreshTable();
    }

    /** Корневая панель окна (для сцены). */
    public Region getView() {
        return root;
    }

    private TableView<Contact> buildTable() {
        table.getColumns().add(column("Тип", MainController::typeOf));
        table.getColumns().add(column("Имя", Contact::getName));
        table.getColumns().add(column("Телефон", Contact::getPhone));
        table.getColumns().add(column("E-mail", Contact::getEmail));
        table.getColumns().add(column("Организация", Contact::getOrganization));
        table.getColumns().add(column("Должность", MainController::positionOf));
        table.getColumns().add(column("Внутр. номер", MainController::internalNumberOf));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Контактов нет. Загрузите CSV или добавьте контакт."));
        return table;
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

    private BorderPane buildBottomPanel() {
        BorderPane bottom = new BorderPane();

        Button loadButton = new Button("Загрузить CSV");
        Button saveButton = new Button("Сохранить CSV");
        Button addButton = new Button("Добавить");
        addButton.setTooltip(new Tooltip("Добавить можно только редактируемый тип (корпоративный)"));

        loadButton.setOnAction(e -> onLoadCsv());
        saveButton.setOnAction(e -> onSaveCsv());
        addButton.setOnAction(e -> onAddContact());
        editButton.setOnAction(e -> onEditContact());

        HBox buttons = new HBox(8,
                loadButton, saveButton, addButton, editButton);
        buttons.setPadding(new Insets(10));

        bottom.setCenter(statusLabel);
        bottom.setRight(buttons);
        BorderPane.setMargin(statusLabel, new Insets(0, 10, 0, 0));
        return bottom;
    }

    private void onLoadCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Загрузить CSV");
        chooser.getExtensionFilters().add(CSV_FILTER);
        File file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file == null) {
            return;
        }

        Task<CsvLoadResult> task = new Task<>() {
            @Override
            protected CsvLoadResult call() throws Exception {
                return new CsvLoader().load(file.toPath());
            }
        };
        task.setOnSucceeded(e -> {
            CsvLoadResult result = task.getValue();
            service.replaceAll(result.contacts());
            refreshTable();
            setStatus("Загружено " + result.contacts().size()
                    + ", пропущено битых строк: " + result.skippedLines());
        });
        task.setOnFailed(e -> showError("Ошибка загрузки CSV", task.getException()));

        runInBackground(task);
    }

    private void onSaveCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Сохранить CSV");
        chooser.getExtensionFilters().add(CSV_FILTER);
        File file = chooser.showSaveDialog(root.getScene().getWindow());
        if (file == null) {
            return;
        }

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                new CsvWriter().write(file.toPath(), service.getAll());
                return null;
            }
        };
        task.setOnSucceeded(e -> setStatus("Сохранено контактов: " + service.size()));
        task.setOnFailed(e -> showError("Ошибка сохранения CSV", task.getException()));

        runInBackground(task);
    }

    private void onAddContact() {
        ContactDialog.showAdd(root.getScene().getWindow()).ifPresent(contact -> {
            service.add(contact);
            refreshTable();
            setStatus("Добавлен контакт: " + contact.getName());
        });
    }

    private void onEditContact() {
        Contact selected = table.getSelectionModel().getSelectedItem();
        if (!(selected instanceof CorporateContact corporate)) {
            return;
        }
        ContactDialog.showEdit(root.getScene().getWindow(), corporate).ifPresent(updated -> {
            int index = table.getItems().indexOf(selected);
            service.replace(index, updated);
            refreshTable();
            setStatus("Изменён контакт: " + updated.getName());
        });
    }

    private void updateEditButton(Contact selected) {
        editButton.setDisable(!(selected instanceof Editable));
    }

    private void refreshTable() {
        table.getItems().setAll(service.getAll());
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }

    /** Тяжёлая операция выполняется в фоновом потоке, чтобы не блокировать UI. */
    private static void runInBackground(Task<?> task) {
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void showError(String title, Throwable error) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(String.valueOf(error.getMessage()));
        alert.showAndWait();
    }
}