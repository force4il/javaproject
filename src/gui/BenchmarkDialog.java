package gui;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Замеры вставки и поиска для 1000, 10000 и 50000 товаров. */
public final class BenchmarkDialog {
    private static final int[] SIZES = {1_000, 10_000, 50_000};

    private BenchmarkDialog() {}

    public static void show(Stage owner) {
        Dialog<Void> dlg = new Dialog<>();
        dlg.initOwner(owner);
        dlg.setTitle("Скорость B-дерева");
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.setResizable(true);

        TableView<BenchmarkRunner.SpeedRow> table = new TableView<>();
        table.getColumns().add(column("Элементов", r -> String.valueOf(r.elements())));
        table.getColumns().add(column("Узлов", r -> String.valueOf(r.nodes())));
        table.getColumns().add(column("Вставка всех, мс",
                r -> fmt(r.insertMillis())));
        table.getColumns().add(column("Вставка одной, мкс",
                r -> fmt(r.averageInsertMicros())));
        table.getColumns().add(column("Поиск одного, мкс",
                r -> fmt(r.searchMicros())));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefSize(900, 280);

        Label info = new Label("Подготовка измерений...");
        Label note = new Label("Поиск одного существующего артикула усреднён по 20 000 вызовам.");
        ProgressBar progress = new ProgressBar(0);
        progress.setPrefWidth(860);
        VBox content = new VBox(10, info, progress, table, note);
        content.setPadding(new Insets(12));
        dlg.getDialogPane().setContent(content);

        Task<List<BenchmarkRunner.SpeedRow>> task = new Task<>() {
            @Override protected List<BenchmarkRunner.SpeedRow> call() {
                List<BenchmarkRunner.SpeedRow> result = new ArrayList<>();
                for (int i = 0; i < SIZES.length; i++) {
                    if (isCancelled()) break;
                    int n = SIZES[i];
                    updateMessage("Измерение: " + n + " элементов");
                    BenchmarkRunner.SpeedRow row =
                            BenchmarkRunner.runSpeed(n, 12345L + n, this::isCancelled);
                    if (row == null) break;
                    result.add(row);
                    int completed = i + 1;
                    updateProgress(completed, SIZES.length);
                    Platform.runLater(() -> table.getItems().setAll(new ArrayList<>(result)));
                }
                return result;
            }
        };
        info.textProperty().bind(task.messageProperty());
        progress.progressProperty().bind(task.progressProperty());
        task.setOnSucceeded(e -> {
            info.textProperty().unbind();
            info.setText(task.isCancelled() ? "Отменено" : "Измерения завершены");
        });
        task.setOnFailed(e -> {
            info.textProperty().unbind();
            info.setText("Ошибка: " + task.getException());
        });
        dlg.setOnCloseRequest(e -> task.cancel());

        Thread thread = new Thread(task, "benchmark-speed");
        thread.setDaemon(true);
        thread.start();
        dlg.showAndWait();
    }

    private static TableColumn<BenchmarkRunner.SpeedRow, String> column(
            String title, java.util.function.Function<BenchmarkRunner.SpeedRow, String> getter) {
        TableColumn<BenchmarkRunner.SpeedRow, String> col = new TableColumn<>(title);
        col.setCellValueFactory(d -> new ReadOnlyStringWrapper(getter.apply(d.getValue())));
        return col;
    }

    private static String fmt(double value) {
        return String.format(Locale.forLanguageTag("ru-RU"), "%.3f", value);
    }
}
