
package gui;

import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.stage.Stage;

import model.CatalogItem;
import serializable.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;

public final class FormatBenchmarkDialog {

    private FormatBenchmarkDialog() {}

    public static void show(Stage owner, List<CatalogItem> items) {
        if (items.isEmpty()) {
            Dialogs.showInfo(owner, "Сравнение форматов", "Сначала добавьте или загрузите товары");
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Сравнение BIN и JSON");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        Label status = new Label("Подготовка...");
        ProgressBar progress = new ProgressBar();

        progress.setPrefWidth(350);

        TextArea results = new TextArea();
        results.setEditable(false);
        results.setPrefSize(520, 230);

        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(10, status, progress, results);

        content.setPadding(new javafx.geometry.Insets(15));

        dialog.getDialogPane().setContent(content);

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                Path folder = Files.createTempDirectory("format-benchmark-");
                try {
                    updateMessage("Измерение BIN...");
                    updateProgress(0, 2);

                    FormatBenchmark.Result bin =
                            FormatBenchmark.measure(new BinaryCatalogSerializer(), items, folder);
                    if (isCancelled())
                        throw new CancellationException();

                    updateMessage("Измерение JSON...");
                    updateProgress(1, 2);

                    FormatBenchmark.Result json =
                            FormatBenchmark.measure(new JsonCatalogSerializer(), items, folder);
                    if (isCancelled())
                        throw new CancellationException();

                    updateProgress(2, 2);

                    return String.format(
                            Locale.forLanguageTag("ru-RU"),
                            "Количество товаров: %d%n" + "Прогонов: 5%n%n" + "%-8s %12s %14s %14s%n"
                                    + "%-8s %12d %14.3f %14.3f%n" + "%-8s %12d %14.3f %14.3f%n%n" + "Размер — байты%n"
                                    + "Время — миллисекунды%n", items.size(), "Формат", "Размер",
                                    "Запись", "Чтение", "BIN", bin.bytes(), bin.saveMillis(), bin.loadMillis(),
                                    "JSON", json.bytes(), json.saveMillis(), json.loadMillis()
                    );
                } finally {
                    Files.deleteIfExists(folder);
                }
            }
        };

        status.textProperty().bind(task.messageProperty());
        progress.progressProperty().bind(task.progressProperty());

        task.setOnSucceeded(e -> {
            status.textProperty().unbind();
            status.setText("Измерения завершены");
            results.setText(task.getValue());
        });

        task.setOnFailed(e -> {
            status.textProperty().unbind();
            status.setText("Ошибка измерения");
            results.setText(String.valueOf(task.getException()));
        });

        task.setOnCancelled(e -> {
            status.textProperty().unbind();
            status.setText("Измерения отменены");
        });

        dialog.setOnCloseRequest(e -> task.cancel(true));

        Thread thread = new Thread(task, "format-benchmark");
        thread.setDaemon(true);
        thread.start();

        dialog.showAndWait();
    }
}
