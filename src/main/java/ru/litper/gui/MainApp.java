package ru.litper.gui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ru.litper.service.ContactService;

/**
 * Точка входа JavaFX-приложения.
 *
 * <p>Создаёт сервис контактов и отдаёт управление {@link MainController}.
 * Класс выделен отдельно от {@code Main}, чтобы запустить приложение
 * без требований к module-path (см. Main).</p>
 */
public class MainApp extends Application {

    @Override
    public void start(Stage stage) {
        MainController controller = new MainController(new ContactService());
        Scene scene = new Scene(controller.getView(), 950, 600);
        stage.setTitle("Справочник контактов — вариант 4");
        stage.setScene(scene);
        stage.show();
    }
}