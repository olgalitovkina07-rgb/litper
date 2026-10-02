package ru.litper.gui;

import javafx.concurrent.Task;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;

import java.util.function.Consumer;

/**
 * Запускает тяжёлые операции в фоновом потоке и показывает их прогресс.
 *
 * <p>Работа всегда идёт через {@link Task}: тяжёлое — в {@code call()},
 * обновление интерфейса — в обработчиках {@code setOnSucceeded/setOnFailed},
 * которые JavaFX выполняет в FX-потоке. Отмена кооперативная:
 * фоновая операция сама проверяет {@link Task#isCancelled()} и прерывается.</p>
 */
final class TaskRunner {

    private final ProgressBar progressBar = new ProgressBar(0);
    private final Button cancelButton = new Button("Отменить");

    private Task<?> currentTask;

    TaskRunner() {
        progressBar.setPrefWidth(170);
        progressBar.setVisible(false);
        cancelButton.setDisable(true);
        cancelButton.setOnAction(e -> cancel());
    }

    ProgressBar getProgressBar() {
        return progressBar;
    }

    Button getCancelButton() {
        return cancelButton;
    }

    boolean isBusy() {
        return currentTask != null;
    }

    /** Просит текущую операцию прерваться. */
    void cancel() {
        if (currentTask != null) {
            currentTask.cancel();
        }
    }

    /**
     * Запускает задачу в отдельном потоке.
     *
     * @param task        задача с тяжёлой работой в {@code call()}
     * @param onFinished  вызывается в FX-потоке при любом исходе (успех/ошибка/отмена)
     * @param onSucceeded что сделать в FX-потоке после успеха
     * @param onFailed    что сделать в FX-потоке после ошибки
     */
    void run(Task<?> task, Runnable onFinished, Runnable onSucceeded, Consumer<Throwable> onFailed) {
        currentTask = task;
        cancelButton.setDisable(false);
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

        // Если задача сообщает прогресс (updateProgress), бар становится определённым.
        task.progressProperty().addListener((obs, oldValue, newValue) -> {
            double value = newValue.doubleValue();
            if (value >= 0 && !Double.isNaN(value)) {
                progressBar.setProgress(value);
            }
        });
        task.setOnSucceeded(event -> {
            finish();
            onSucceeded.run();
            onFinished.run();
        });
        task.setOnFailed(event -> {
            finish();
            onFailed.accept(task.getException());
            onFinished.run();
        });
        task.setOnCancelled(event -> {
            finish();
            onFinished.run();
        });

        Thread thread = new Thread(task, "litper-background");
        thread.setDaemon(true);
        thread.start();
    }

    private void finish() {
        currentTask = null;
        progressBar.setProgress(0);
        progressBar.setVisible(false);
        cancelButton.setDisable(true);
    }
}