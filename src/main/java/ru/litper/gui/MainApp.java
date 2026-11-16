package ru.litper.gui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import ru.litper.server.ServerApp;
import ru.litper.service.ContactService;

/**
 * Точка входа JavaFX-приложения.
 *
 * <p>Spring Boot поднимается в {@link #init()}, а в {@link #start(Stage)} GUI
 * получает общий бин {@link ContactService}. GUI и REST API работают с одним
 * и тем же хранилищем в одном процессе.</p>
 */
public class MainApp extends Application {

    private ConfigurableApplicationContext spring;

    @Override
    public void init() {
        spring = new SpringApplicationBuilder(ServerApp.class)
                .headless(false)
                .run();
    }

    @Override
    public void start(Stage stage) {
        ContactService service = spring.getBean(ContactService.class);
        MainController controller = new MainController(service);
        Scene scene = new Scene(controller.getView(), 1360, 760);
        stage.setTitle("Справочник контактов — вариант 4 (Trie + REST API)");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        if (spring != null) {
            spring.close();
        }
    }
}