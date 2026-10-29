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
import java.util.concurrent.CancellationException;

public final class BenchmarkDialog {

    private static final int[] SIZES = {10_000, 100_000};
    private static final double WIDTH = 560;
    private static final double HEIGHT = 380;

    private static final String MSG_RUNNING = "Выполняется…";
    private static final String MSG_DONE    = "Готово";
    private static final String MSG_CANCELLED = "Отменено";

    private BenchmarkDialog() {}

    public static void show(Stage owner) {
        Dialog<Void> dlg = new Dialog<>();
        dlg.initOwner(owner);
        dlg.setTitle("Замеры производительности");
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.setResizable(true);

        //Инициализация таблицы и колонок
        TableView<BenchmarkRunner.Row> tv = new TableView<>();

        TableColumn<BenchmarkRunner.Row, String> c1 = new TableColumn<>("Операция");
        c1.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().operation()));

        TableColumn<BenchmarkRunner.Row, String> c2 = new TableColumn<>("N");
        c2.setCellValueFactory(d -> new ReadOnlyStringWrapper(String.valueOf(d.getValue().n())));

        TableColumn<BenchmarkRunner.Row, String> c3 = new TableColumn<>("Время, мс");
        c3.setCellValueFactory(d -> new ReadOnlyStringWrapper(
                String.format("%.2f", d.getValue().millis())));

        // добавляем колонки
        tv.getColumns().addAll(c1, c2, c3);

        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tv.setPrefSize(WIDTH, HEIGHT);

        Label info = new Label(MSG_RUNNING);
        ProgressBar pb = new ProgressBar();
        pb.setPrefWidth(WIDTH - 40);

        VBox box = new VBox(8, info, pb, tv);
        box.setPadding(new Insets(12));
        dlg.getDialogPane().setContent(box);

        Task<Void> task = new Task<>() {
            @Override protected Void call() {
                List<BenchmarkRunner.Row> all = new ArrayList<>();
                for (int n : SIZES) {
                    if (isCancelled())
                        break;

                    updateMessage("N = " + n);

                    List<BenchmarkRunner.Row> rows = BenchmarkRunner.run(
                            n, System.nanoTime(), this::isCancelled);
                    all.addAll(rows);

                    if (isCancelled())
                        break;

                    //обновляем таблицу
                    List<BenchmarkRunner.Row> snapshot = List.copyOf(all);
                    Platform.runLater(() -> tv.getItems().setAll(snapshot));
                }
                if (isCancelled()) {
                    throw new CancellationException("замер отменён");
                }

                return null;
            }
        };

        // Обработчики состояний
        task.setOnSucceeded(e -> {
            info.setText(MSG_DONE);
            pb.setProgress(1);
        });

        task.setOnCancelled(e -> {
            info.setText(MSG_CANCELLED);
            pb.setProgress(0);
        });

        task.setOnFailed(e -> {
            info.setText("Ошибка выполнения");
            pb.setProgress(0);
            Throwable ex = task.getException();
            Dialogs.showError(owner, "Ошибка замера производительности",
                    ex == null ? "Неизвестная ошибка" : ex.getMessage());
        });

        // Обновление текста прогресса
        task.messageProperty().addListener((o, a, b) -> info.setText(b));

        // Запуск
        Thread t = new Thread(task, "benchmark");
        t.setDaemon(true);
        t.start();

        // Отмена при закрытии диалога
        dlg.setOnCloseRequest(e -> task.cancel());
        dlg.showAndWait();
    }
}