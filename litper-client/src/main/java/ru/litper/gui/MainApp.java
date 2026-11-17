package ru.litper.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ru.litper.client.api.ApiClient;
import ru.litper.client.api.HttpApiClient;
import ru.litper.client.service.RemoteContactService;

public class MainApp extends Application {

    private static final String DEFAULT_BASE_URL = "http://localhost:8080";

    @Override
    public void start(Stage stage) {
        String baseUrl = getParameters().getNamed().getOrDefault("baseUrl", DEFAULT_BASE_URL);
        ApiClient api = new HttpApiClient(baseUrl);
        RemoteContactService service = new RemoteContactService(api);
        MainController controller = new MainController(service, baseUrl);
        Scene scene = new Scene(controller.getView(), 1360, 760);
        stage.setTitle("Справочник контактов — вариант 4 (Trie + REST API)");
        stage.setScene(scene);
        stage.show();
        Platform.runLater(controller::reload);
    }
}
