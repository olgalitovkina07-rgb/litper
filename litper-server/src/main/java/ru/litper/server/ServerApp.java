package ru.litper.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Конфигурация Spring Boot-сервера.
 *
 * <p>Сканирование начинается с {@code ru.litper}, чтобы найти бины GUI-независимых
 * слоёв: {@code service}, {@code server.service} и контроллеры.</p>
 */
@SpringBootApplication(scanBasePackages = "ru.litper")
public class ServerApp {

    public static void main(String[] args) {
        SpringApplication.run(ServerApp.class, args);
    }
}
