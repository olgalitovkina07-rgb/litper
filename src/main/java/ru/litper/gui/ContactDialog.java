package ru.litper.gui;

import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;
import ru.litper.model.Contact;
import ru.litper.model.ContactType;
import ru.litper.model.CorporateContact;

import java.util.List;
import java.util.Optional;

/**
 * Диалог добавления и редактирования контакта.
 *
 * <p>Через GUI разрешено создавать и изменять только {@link CorporateContact}
 * (редактируемый тип). Аварийный контакт через этот диалог создать нельзя.</p>
 */
public final class ContactDialog {

    private final Dialog<Contact> dialog = new Dialog<>();
    private final ComboBox<ContactType> typeBox =
            new ComboBox<>(FXCollections.observableArrayList(ContactType.CORPORATE));
    private final TextField nameField = new TextField();
    private final TextField phoneField = new TextField();
    private final TextField emailField = new TextField();
    private final TextField organizationField = new TextField();
    private final TextField positionField = new TextField();
    private final TextField internalNumberField = new TextField();

    private CorporateContact result;

    private ContactDialog(Window owner, CorporateContact existing) {
        dialog.initOwner(owner);
        dialog.setTitle(existing == null ? "Добавление контакта" : "Изменение контакта");
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

        typeBox.getSelectionModel().select(ContactType.CORPORATE);
        typeBox.setDisable(true); // создаём/изменяем только корпоративный контакт

        if (existing != null) {
            nameField.setText(existing.getName());
            phoneField.setText(existing.getPhone());
            emailField.setText(existing.getEmail());
            organizationField.setText(existing.getOrganization());
            positionField.setText(existing.getPosition());
            internalNumberField.setText(existing.getInternalNumber());
        }

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(16));
        grid.addRow(0, new Label("Тип:"), typeBox);
        grid.addRow(1, new Label("Имя:"), nameField);
        grid.addRow(2, new Label("Телефон:"), phoneField);
        grid.addRow(3, new Label("E-mail:"), emailField);
        grid.addRow(4, new Label("Организация:"), organizationField);
        grid.addRow(5, new Label("Должность:"), positionField);
        grid.addRow(6, new Label("Внутр. номер:"), internalNumberField);
        dialog.getDialogPane().setContent(grid);

        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.addEventFilter(ActionEvent.ACTION, this::onOk);

        dialog.setResultConverter(buttonType ->
                buttonType == ButtonType.OK ? result : null);
    }

    private void onOk(ActionEvent event) {
        CorporateContact candidate = new CorporateContact(
                nameField.getText().trim(),
                phoneField.getText().trim(),
                emailField.getText().trim(),
                organizationField.getText().trim(),
                positionField.getText().trim(),
                internalNumberField.getText().trim());

        List<String> errors = candidate.validate();
        if (errors.isEmpty()) {
            result = candidate;
            return;
        }

        showErrors(errors);
        event.consume(); // не закрываем диалог — данные некорректны
    }

    private void showErrors(List<String> errors) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(dialog.getOwner());
        alert.setTitle("Некорректные данные");
        alert.setHeaderText("Исправьте ошибки:");
        alert.setContentText(String.join("\n", errors));
        alert.showAndWait();
    }

    /**
     * Открывает диалог добавления нового (корпоративного) контакта.
     */
    public static Optional<Contact> showAdd(Window owner) {
        return new ContactDialog(owner, null).dialog.showAndWait();
    }

    /**
     * Открывает диалог редактирования существующего корпоративного контакта.
     */
    public static Optional<Contact> showEdit(Window owner, CorporateContact existing) {
        return new ContactDialog(owner, existing).dialog.showAndWait();
    }
}