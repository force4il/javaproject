package gui;

import exceptions.AppException;
import exceptions.CsvException;
import exceptions.TreeOperationException;
import javafx.concurrent.Task;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.Stage;

import java.util.List;
import java.util.function.Consumer;

public final class OperationSupport {

    private static final String BUSY_TITLE = "Операция";

    private final Stage stage;
    private final Label status;
    private final Label stats;
    private final ProgressBar progress;
    private final Button cancel;
    private final List<Button> ioButtons;

    private volatile Task<?> current;
    private Runnable onIdle = () -> {};

    public OperationSupport(Stage stage, ProductView view) {
        this.stage = stage;
        this.status = view.statusLabel;
        this.stats = view.statsLabel;
        this.progress = view.progressBar;
        this.cancel = view.cancelButton;
        this.ioButtons = List.of(view.loadButton, view.saveButton, view.addButton,
                view.editButton, view.delButton, view.genButton,
                view.benchButton, view.clearButton,
                view.raceDemoButton);
        cancel.setOnAction(e -> cancel());
    }

    public void setStatus(String text) { status.setText(text); }
    public void setStats(String text)  { stats.setText(text); }
    public void setOnIdle(Runnable r)  { this.onIdle = r == null ? () -> {} : r; }

    public <T> void run(Task<T> task, String busyMessage,
                        Consumer<T> onSuccess, String actionTitle) {
        task.messageProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null && !newValue.isBlank())
                status.setText(newValue);
        });

        if (current != null && current.isRunning()) {
            Dialogs.showInfo(stage, BUSY_TITLE, "Дождитесь завершения текущей операции");
            return;
        }

        current = task;
        busy(busyMessage);

        progress.progressProperty().unbind();
        progress.progressProperty().bind(task.progressProperty());

        task.setOnSucceeded(e -> { idle(); current = null; onSuccess.accept(task.getValue()); });
        task.setOnFailed(e -> {
            idle(); current = null;
            handle(actionTitle, task.getException());
            status.setText(actionTitle + ": ошибка");
        });
        task.setOnCancelled(e -> { idle(); current = null; status.setText(actionTitle + " отменено"); });

        Thread t = new Thread(task, "bg-task");
        t.setDaemon(true);
        t.start();
    }

    public void handle(String action, Throwable ex) {
        switch (ex) {
            case TreeOperationException te -> Dialogs.showTreeError(stage, action, te);
            case CsvException ce -> Dialogs.showCsvError(stage, action, ce);
            case AppException ae -> Dialogs.showAppError(stage, action, ae);
            case null, default -> Dialogs.showError(stage, action + " — ошибка",
                    ex == null ? "Неизвестная ошибка" : String.valueOf(ex.getMessage()));
        }
    }

    private void busy(String message) {
        progress.progressProperty().unbind();
        status.setText(message);
        progress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progress.setVisible(true);
        progress.setManaged(true);
        cancel.setVisible(true);
        cancel.setManaged(true);
        for (Button b : ioButtons)
            b.setDisable(true);
    }

    private void idle() {
        progress.progressProperty().unbind();
        progress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progress.setVisible(false);
        progress.setManaged(false);
        cancel.setVisible(false);
        cancel.setManaged(false);
        for (Button b : ioButtons)
            b.setDisable(false);

        onIdle.run();
    }

    private void cancel() {
        Task<?> t = current;
        if (t != null && t.isRunning())
            t.cancel(true);
    }
}