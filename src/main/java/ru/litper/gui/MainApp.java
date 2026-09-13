package ru.litper.gui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) {
        Label hello = new Label("Hello world");
        stage.setScene(new Scene(hello, 400, 300));
        stage.setTitle("Справочник контактов");
        stage.show();
    }
}