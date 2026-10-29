package gui;

import csv.CsvLoader;
import exceptions.ErrorCode;
import exceptions.TreeOperationException;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import model.CatalogItem;
import model.Product;
import service.RaceConditionRunner;
import tree.BTree;

import java.io.File;

public final class ConcurrentDemoDialog {

    private static final int MIN_THREADS = 1;
    private static final int MAX_THREADS = 100;

    private final BTree<CatalogItem> tree = new BTree<>();
    private final BTreeVisual visualizer = new BTreeVisual();

    private final Label statusLabel = new Label("Ожидание...");
    private final CheckBox syncToggle = new CheckBox("Синхронизировать вставку");
    private final TextField threadsField = new TextField("10");

    private final Button btnLoad  = new Button("Загрузить CSV");
    private final Button btnAdd = new Button("Запустить вставку");
    private final Button btnClear = new Button("Очистить");

    private boolean busy = false;
    private boolean treeDamaged = false;

    public static void show(Stage owner) {
        new ConcurrentDemoDialog().showInternal(owner);
    }

    private void showInternal(Stage owner) {
        Dialog<Void> dlg = new Dialog<>();
        dlg.initOwner(owner);
        dlg.setTitle("Демонстрация Race Condition");
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.setResizable(true);

        threadsField.setPrefColumnCount(6);
        syncToggle.setSelected(false);
        statusLabel.setWrapText(true);

        btnLoad.setOnAction(e -> onLoad(owner));
        btnAdd.setOnAction(e -> onAdd(owner));
        btnClear.setOnAction(e -> {
            tree.clear();
            treeDamaged = false;
            visualizer.setTree(tree);
            statusLabel.setText("Дерево очищено");
            statusLabel.setStyle("-fx-text-fill: black;");
        });

        HBox firstRow = new HBox(10, btnLoad,
                new Label("Количество потоков:"), threadsField, btnAdd, btnClear);
        firstRow.setAlignment(Pos.CENTER_LEFT);

        VBox toolbar = new VBox(8, firstRow, syncToggle, statusLabel);
        toolbar.setPadding(new Insets(10));

        visualizer.setTree(tree);
        visualizer.setPrefSize(800, 500);

        BorderPane root = new BorderPane();
        root.setTop(toolbar);
        root.setCenter(visualizer);
        root.setPrefSize(850, 600);

        dlg.getDialogPane().setContent(root);
        dlg.showAndWait();
    }

    private void setBusy(boolean value) {
        busy = value;
        btnLoad.setDisable(value);
        btnAdd.setDisable(value);
        btnClear.setDisable(value);
        threadsField.setDisable(value);
        syncToggle.setDisable(value);
    }

    private void onLoad(Stage owner) {
        if (busy)
            return;

        File file = InputDialogs.chooseOpenCsv(owner, null);
        if (file == null)
            return;

        setBusy(true);
        visualizer.setTree(null);

        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                CsvLoader.LoadResult result = new CsvLoader().load(file.toPath());
                tree.clear();
                for (CatalogItem item : result.items()) {
                    try {
                        tree.insert(item.getItemNumber(), item);
                    } catch (TreeOperationException ex) {
                        if (ex.getErrorCode() != ErrorCode.TREE_DUPLICATE_KEY) throw ex;
                    }
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            setBusy(false);
            treeDamaged = false;
            visualizer.setTree(tree);
            statusLabel.setText("Загружено элементов: " + tree.size());
            statusLabel.setStyle("-fx-text-fill: black;");
        });
        task.setOnFailed(e -> {
            setBusy(false);
            treeDamaged = true;
            Dialogs.showError(owner, "Ошибка загрузки",
                    String.valueOf(task.getException()));
        });

        Thread worker = new Thread(task, "csv-load");
        worker.setDaemon(true);
        worker.start();
    }

    private void onAdd(Stage owner) {
        if (busy)
            return;

        if (treeDamaged) {
            Dialogs.showError(owner, "Повреждённое дерево",
                    "Перед следующим запуском очистите дерево или загрузите CSV.");
            return;
        }

        int count;
        try {
            count = Integer.parseInt(threadsField.getText().trim());
        } catch (NumberFormatException ex) {
            showInvalidThreads(owner);
            return;
        }
        if (count < MIN_THREADS || count > MAX_THREADS) {
            showInvalidThreads(owner);
            return;
        }

        final int threadCount = count;
        ProductDialog dialog = new ProductDialog(owner, null, null);
        dialog.showAndWait().ifPresent(product -> runRaceCondition(owner, product, threadCount));
    }

    private void showInvalidThreads(Stage owner) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(owner);
        alert.setTitle("Ошибка количества потоков");
        alert.setHeaderText("Недопустимое количество потоков");
        alert.setContentText("Введите целое число от " + MIN_THREADS + " до " + MAX_THREADS);
        alert.showAndWait();
    }

    private void runRaceCondition(Stage owner, Product product, int threadCount) {
        final int key = product.getItemNumber();
        final int initialSize = tree.size();

        try {
            if (tree.search(key) != null) {
                statusLabel.setText("Ошибка [TREE_001]: ключ " + key
                        + " уже существует в дереве. Вставка отменена.");
                statusLabel.setStyle("-fx-text-fill: red;");
                return;
            }
        } catch (TreeOperationException ex) {
            statusLabel.setText(ex.getMessage());
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        final int expectedInsertions = 1;
        final int expectedSize = initialSize + 1;
        final int expectedDuplicates = threadCount - 1;

        final boolean useSync = syncToggle.isSelected();

        setBusy(true);
        statusLabel.setText("Выполняется...");
        statusLabel.setStyle("-fx-text-fill: blue;");
        visualizer.setTree(null);

        Task<RaceConditionRunner.Result> task = new Task<>() {
            @Override protected RaceConditionRunner.Result call() throws Exception {
                return RaceConditionRunner.run(tree, key, product, threadCount, useSync);
            }
        };

        task.setOnSucceeded(e -> {
            setBusy(false);
            RaceConditionRunner.Result r = task.getValue();
            boolean correct = r.isCorrect(expectedSize, expectedInsertions, expectedDuplicates);

            visualizer.setTree(tree);
            if (visualizer.isCorrupted())
                correct = false;

            treeDamaged = !correct;

            StringBuilder sb = new StringBuilder();
            sb.append(correct ? "Нарушений не обнаружено. " : "Обнаружена проблема при вставке. ");
            sb.append("Режим: ").append(useSync ? "с синхронизацией" : "без синхронизации")
                    .append(". Потоков: ").append(r.threadCount())
                    .append(". Вставляемый ключ: ").append(r.key())
                    .append(". Успешных вставок: ").append(r.successes())
                    .append(". Дубликатов: ").append(r.duplicates())
                    .append(". Размер дерева: ").append(r.actualSize())
                    .append(" (ожидалось ").append(expectedSize).append(").");

            if (!r.invariantProblem().isEmpty())
                sb.append(" Причина: ").append(r.invariantProblem());

            if (r.firstError() != null)
                sb.append(" Первая ошибка: ").append(r.firstError().getClass().getSimpleName())
                        .append(": ").append(r.firstError().getMessage());

            if (treeDamaged)
                sb.append(" Перед повтором очистите дерево.");

            statusLabel.setText(sb.toString());
            statusLabel.setStyle(correct ? "-fx-text-fill: green;" : "-fx-text-fill: red;");
        });

        task.setOnFailed(e -> {
            setBusy(false);
            treeDamaged = true;
            statusLabel.setText("Ошибка выполнения: " + task.getException());
            statusLabel.setStyle("-fx-text-fill: red;");
        });

        Thread coordinator = new Thread(task, "race-coordinator");
        coordinator.setDaemon(true);
        coordinator.start();
    }
}