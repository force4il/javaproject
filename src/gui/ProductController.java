package gui;

import csv.CsvLoader;
import csv.CsvSave;
import exceptions.CsvException;
import exceptions.TreeOperationException;
import exceptions.TreeStateException;
import model.CatalogItem;
import model.DiscontinuedProduct;
import model.Editable;
import model.Product;
import model.ProductWithWarranty;
import tree.BTree;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;

public final class ProductController {

    private static final String CSV_EXT      = "*.csv";
    private static final String TITLE_LOAD   = "Загрузка";
    private static final String TITLE_SAVE   = "Сохранение";
    private static final String TITLE_EDIT   = "Изменение";
    private static final String TITLE_GEN    = "Генерация";
    private static final String TITLE_BENCH  = "Замеры";

    private final Stage stage;
    private final BorderPane root;
    private final TableView<CatalogItem> table;
    private final ObservableList<CatalogItem> items = FXCollections.observableArrayList();

    private final BTree<CatalogItem> tree = new BTree<>();
    private final BTreeVisual visualizer = new BTreeVisual();

    // Кнопки
    private final Button loadButton   = new Button("Загрузить CSV");
    private final Button saveButton   = new Button("Сохранить CSV");
    private final Button addButton    = new Button("Добавить");
    private final Button editButton   = new Button("Изменить");
    private final Button delButton    = new Button("Удалить");
    private final Button genButton    = new Button("Генерировать…");
    private final Button benchButton  = new Button("Замеры…");
    private final Button clearButton  = new Button("Очистить");
    private final Button cancelButton = new Button("Отмена");

    // Поиск/диапазон
    private final TextField searchField = new TextField();
    private final TextField rangeFrom   = new TextField();
    private final TextField rangeTo     = new TextField();

    // Статус
    private final Label       statusLabel = new Label("");
    private final ProgressBar progressBar = new ProgressBar();
    private final Label       statsLabel  = new Label();

    private Path currentFile;
    private volatile Task<?> currentTask;

    public ProductController(Stage stage) {
        this.stage = stage;
        this.table = buildTable();

        editButton.setDisable(true);
        delButton .setDisable(true);
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            editButton.setDisable(!(sel instanceof Editable));
            delButton .setDisable(sel == null);
        });

        loadButton  .setOnAction(e -> onLoad());
        saveButton  .setOnAction(e -> onSave());
        addButton   .setOnAction(e -> onAdd());
        editButton  .setOnAction(e -> onEdit());
        delButton   .setOnAction(e -> onDelete());
        genButton   .setOnAction(e -> onGenerate());
        benchButton .setOnAction(e -> onBenchmarks());
        clearButton .setOnAction(e -> { tree.clear(); refreshAll();
            statusLabel.setText("Очищено"); });
        cancelButton.setOnAction(e -> cancelCurrent());

        searchField.setPromptText("Артикул");
        searchField.setPrefColumnCount(10);
        searchField.setOnAction(e -> onSearch());

        rangeFrom.setPromptText("от");
        rangeTo  .setPromptText("до");
        rangeFrom.setPrefColumnCount(6);
        rangeTo  .setPrefColumnCount(6);
        Button rangeButton = new Button("Диапазон");
        rangeButton.setOnAction(e -> onRange());
        Button showAll = new Button("Показать всё");
        showAll.setOnAction(e -> refreshTable());

        progressBar.setPrefWidth(180);
        progressBar.setVisible(false);
        progressBar.setManaged(false);
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);

        // Перенос тулбара по строкам вместо сжатия кнопок
        FlowPane toolbar = new FlowPane(8, 8);            // hgap = 8, vgap = 8
        toolbar.setPadding(new Insets(10));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getChildren().addAll(
                loadButton, saveButton, addButton, editButton, delButton,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                genButton, benchButton, clearButton,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                new Label("Поиск:"), searchField,
                new Label("Диапазон:"), rangeFrom, rangeTo, rangeButton,
                showAll,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                cancelButton, progressBar, statusLabel);

// Обёртка: если строк стало слишком много — появится вертикальная прокрутка
        ScrollPane toolbarScroll = new ScrollPane(toolbar);
        toolbarScroll.setFitToWidth(true);                // toolbar тянется на всю ширину
        toolbarScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        toolbarScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        toolbarScroll.setPrefViewportHeight(96);          // примерная высота двух строк тулбара
        toolbarScroll.setMaxHeight(160);
        toolbarScroll.setStyle("-fx-background-color: transparent;");

        HBox statsBar = new HBox(8, new Label("Статистика:"), statsLabel);
        statsBar.setPadding(new Insets(6, 10, 6, 10));
        statsBar.setStyle("-fx-background-color: #eef2f7;");

        SplitPane split = new SplitPane(table, visualizer);
        split.setDividerPositions(0.55);
        SplitPane.setResizableWithParent(visualizer, true);

        root = new BorderPane();
        root.setTop(new VBox(toolbarScroll, statsBar));   // ← вместо toolbar кладём toolbarScroll
        root.setCenter(split);
    }

    public BorderPane getView() { return root; }

    // =====================================================================
    //  Таблица
    // =====================================================================
    private TableView<CatalogItem> buildTable() {
        TableView<CatalogItem> tv = new TableView<>(items);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tv.getColumns().add(column("Тип",       i -> i.getType().getCsvName()));
        tv.getColumns().add(column("Артикул",   i -> String.valueOf(i.getItemNumber())));
        tv.getColumns().add(column("Название",  CatalogItem::getProductName));
        tv.getColumns().add(column("Категория", CatalogItem::getCategory));
        tv.getColumns().add(column("Цена",      i -> String.valueOf(i.getPrice())));
        tv.getColumns().add(column("Остаток",   i -> String.valueOf(i.getRemainder())));
        tv.getColumns().add(column("Гарантия до", this::warrantyEndOf));
        return tv;
    }

    private static TableColumn<CatalogItem, String> column(String title,
                                                           Function<CatalogItem, String> ex) {
        TableColumn<CatalogItem, String> col = new TableColumn<>(title);
        col.setCellValueFactory(d -> new ReadOnlyStringWrapper(ex.apply(d.getValue())));
        return col;
    }

    private String warrantyEndOf(CatalogItem item) {
        if (item instanceof ProductWithWarranty pw && pw.getStartOfWarranty() != null)
            return pw.getWarrantyEndDate().toString();
        return "";
    }

    // =====================================================================
    //  Действия
    // =====================================================================
    private void onLoad() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Открыть CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", CSV_EXT));
        applyInitialDir(chooser);

        File file = chooser.showOpenDialog(stage);
        if (file == null) return;
        Path path = file.toPath();

        Task<CsvLoader.LoadResult> task = new Task<>() {
            @Override protected CsvLoader.LoadResult call() throws Exception {
                CsvLoader.LoadResult r = new CsvLoader().load(path);
                tree.clear();
                for (CatalogItem it : r.items()) {
                    if (isCancelled()) break;
                    try { tree.insert(it.getItemNumber(), it); }
                    catch (TreeOperationException dup) { /* пропускаем дубликат */ }
                }
                return r;
            }
        };
        task.setOnSucceeded(e -> {
            CsvLoader.LoadResult r = task.getValue();
            currentFile = path;
            refreshAll();
            statusLabel.setText("Загружено: " + path.getFileName()
                    + " (пропущено строк: " + r.skippedRows().size() + ")");
            if (!r.skippedRows().isEmpty()) showSkipped(r.skippedRows());
        });
        task.setOnFailed(e -> {
            statusLabel.setText("Ошибка загрузки: " + path.getFileName());
            handleFailure(TITLE_LOAD, task.getException());
        });
        task.setOnCancelled(e -> statusLabel.setText("Загрузка отменена"));
        runInBackground(task, "Загрузка…");
    }

    private void onSave() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Сохранить CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", CSV_EXT));
        chooser.setInitialFileName(currentFile != null
                ? currentFile.getFileName().toString() : "data.csv");
        applyInitialDir(chooser);
        File file = chooser.showSaveDialog(stage);
        if (file == null) return;
        Path path = file.toPath();
        var snapshot = new ArrayList<>(tree.inOrder());

        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                CsvSave.save(snapshot, path); return null;
            }
        };
        task.setOnSucceeded(e -> {
            currentFile = path;
            statusLabel.setText("Сохранено: " + path.getFileName());
        });
        task.setOnFailed(e -> {
            statusLabel.setText("Ошибка сохранения: " + path.getFileName());
            handleFailure(TITLE_SAVE, task.getException());
        });
        runInBackground(task, "Сохранение…");
    }

    private void onAdd() {
        ProductDialog d = new ProductDialog(stage, null);
        Optional<Product> res = d.showAndWait();
        res.ifPresent(p -> {
            try {
                tree.insert(p.getItemNumber(), p);
                refreshAll();
                statusLabel.setText("Добавлено: " + p.getItemNumber());
            } catch (TreeOperationException ex) {
                Dialogs.showTreeError(stage, "Добавление", ex);
            }
        });
    }

    private void onEdit() {
        CatalogItem sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        if (!(sel instanceof Product existing)) {
            Dialogs.showInfo(stage, TITLE_EDIT,
                    "Этот тип записи доступен только для чтения.");
            return;
        }
        ProductDialog d = new ProductDialog(stage, existing);
        d.showAndWait().ifPresent(updated -> {
            int oldKey = existing.getItemNumber();
            int newKey = updated.getItemNumber();
            try {
                // всегда удаляем старый ключ, потом вставляем новый
                tree.delete(oldKey);
                tree.insert(newKey, updated);
                refreshAll();
                statusLabel.setText("Изменено: " + newKey);
            } catch (TreeOperationException ex) {
                // откат
                try { tree.insert(oldKey, existing); }
                catch (TreeOperationException ignore) {}
                Dialogs.showTreeError(stage, "Изменение", ex);
            }
        });
    }

    private void onDelete() {
        CatalogItem sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        try {
            tree.delete(sel.getItemNumber());
            refreshAll();
            statusLabel.setText("Удалено: " + sel.getItemNumber());
        } catch (TreeOperationException ex) {
            Dialogs.showTreeError(stage, "Удаление", ex);
        }
    }

    private void onSearch() {
        String s = searchField.getText().trim();
        if (s.isEmpty()) return;
        int key;
        try { key = Integer.parseInt(s); }
        catch (NumberFormatException e) {
            Dialogs.showError(stage, "Поиск", "Артикул должен быть числом");
            return;
        }
        CatalogItem found;
        try { found = tree.search(key); }
        catch (TreeStateException ex) {
            Dialogs.showError(stage, "Поиск", ex.getMessage());
            return;
        }
        visualizer.redraw();
        updateStats();
        if (found == null) {
            statusLabel.setText("Не найдено: " + key);
            table.getSelectionModel().clearSelection();
        } else {
            statusLabel.setText("Найдено: " + key);
            table.getSelectionModel().select(found);
            table.scrollTo(found);
        }
    }

    private void onRange() {
        Integer from = parseOrNull(rangeFrom.getText());
        Integer to   = parseOrNull(rangeTo.getText());
        if (from == null || to == null) {
            Dialogs.showError(stage, "Диапазон", "Введите оба числа");
            return;
        }
        try {
            List<CatalogItem> res = tree.rangeScan(from, to);
            items.setAll(res);
            visualizer.redraw();
            updateStats();
            statusLabel.setText("Диапазон [" + from + ".." + to + "]: " + res.size());
        } catch (TreeOperationException ex) {
            Dialogs.showTreeError(stage, "Диапазон", ex);
        }
    }

    private void onGenerate() {
        Dialog<Void> dlg = new Dialog<>();
        dlg.initOwner(stage);
        dlg.setTitle("Генерация данных");
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField countField = new TextField("10000");
        TextField seedField  = new TextField(String.valueOf(System.currentTimeMillis()));
        GridPane gp = new GridPane();
        gp.setHgap(8); gp.setVgap(8); gp.setPadding(new Insets(12));
        gp.add(new Label("Количество:"), 0, 0); gp.add(countField, 1, 0);
        gp.add(new Label("Seed:"),       0, 1); gp.add(seedField,  1, 1);
        dlg.getDialogPane().setContent(gp);

        final int    count;
        final long   seed;
        Button okBtn = (Button) dlg.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
            try {
                Integer.parseInt(countField.getText().trim());
                Long.parseLong(seedField.getText().trim());
            } catch (NumberFormatException e) {
                Dialogs.showError(stage, "Генерация", "Введите корректные числа");
                evt.consume();
            }
        });
        if (dlg.showAndWait().isEmpty()) return;
        count = Integer.parseInt(countField.getText().trim());
        seed  = Long.parseLong(seedField.getText().trim());
        if (count <= 0) return;

        Task<Integer> task = new Task<>() {
            @Override protected Integer call() {
                Random rnd = new Random(seed);
                java.util.HashSet<Integer> used = new java.util.HashSet<>(count * 2);
                int inserted = 0;
                for (int i = 0; i < count; i++) {
                    if (isCancelled()) return inserted;
                    int num;
                    do { num = rnd.nextInt(Math.max(1, count * 10)) + 1; } while (!used.add(num));
                    int kind = rnd.nextInt(3);
                    String name = "Item" + num;
                    String cat  = "Cat" + (rnd.nextInt(5) + 1);
                    int price = 100 + rnd.nextInt(900);
                    int rem   = rnd.nextInt(500);
                    CatalogItem it = switch (kind) {
                        case 0 -> new Product(num, name, cat, price, rem);
                        case 1 -> new ProductWithWarranty(num, name, cat, price, rem,
                                LocalDate.now().minusDays(rnd.nextInt(365)),
                                6 + rnd.nextInt(24));
                        default -> new DiscontinuedProduct(num, name, cat, price, rem);
                    };
                    try { tree.insert(num, it); inserted++; }
                    catch (TreeOperationException ignore) {}
                    if ((i & 0xFF) == 0) updateProgress(i, count);
                }
                return inserted;
            }
        };
        task.setOnSucceeded(e -> {
            refreshAll();
            statusLabel.setText("Сгенерировано: " + task.getValue());
        });
        task.setOnFailed(e -> {
            statusLabel.setText("Ошибка генерации");
            handleFailure(TITLE_GEN, task.getException());
        });
        task.setOnCancelled(e -> {
            refreshAll();
            statusLabel.setText("Генерация отменена (частично добавлено)");
        });
        runInBackground(task, "Генерация…");
    }

    private void onBenchmarks() {
        Dialog<Void> dlg = new Dialog<>();
        dlg.initOwner(stage);
        dlg.setTitle("Замеры производительности");
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.setResizable(true);

        TableView<BenchmarkRunner.Row> tv = new TableView<>();
        TableColumn<BenchmarkRunner.Row, String> c1 = new TableColumn<>("Операция");
        c1.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().operation()));
        TableColumn<BenchmarkRunner.Row, String> c2 = new TableColumn<>("N");
        c2.setCellValueFactory(d -> new ReadOnlyStringWrapper(String.valueOf(d.getValue().n())));
        TableColumn<BenchmarkRunner.Row, String> c3 = new TableColumn<>("Время, мс");
        c3.setCellValueFactory(d -> new ReadOnlyStringWrapper(
                String.format("%.2f", d.getValue().millis())));
        tv.getColumns().addAll(c1, c2, c3);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tv.setPrefSize(560, 380);

        Label info = new Label("Выполняется…");
        ProgressBar pb = new ProgressBar();
        pb.setPrefWidth(420);

        VBox box = new VBox(8, info, pb, tv);
        box.setPadding(new Insets(12));
        dlg.getDialogPane().setContent(box);

        Task<Void> task = new Task<>() {
            @Override protected Void call() {
                int[] sizes = {10_000, 100_000};
                List<BenchmarkRunner.Row> all = new ArrayList<>();
                for (int n : sizes) {
                    if (isCancelled()) return null;
                    updateMessage("N = " + n);
                    List<BenchmarkRunner.Row> rows = BenchmarkRunner.run(
                            n, System.nanoTime(), this::isCancelled);
                    all.addAll(rows);
                    Platform.runLater(() -> tv.getItems().setAll(new ArrayList<>(all)));
                }
                Platform.runLater(() -> info.setText("Готово"));
                return null;
            }
        };
        info.textProperty().bind(task.messageProperty());
        Thread t = new Thread(task, "benchmark");
        t.setDaemon(true);
        t.start();

        dlg.setOnCloseRequest(e -> task.cancel());
        dlg.showAndWait();
    }

    // =====================================================================
    //  Служебные
    // =====================================================================
    private void refreshAll() {
        refreshTable();
        visualizer.setTree(tree);
        updateStats();
    }

    private void refreshTable() {
        items.setAll(tree.inOrder());
    }

    private void updateStats() {
        BTree.FillStats s = tree.stats();
        statsLabel.setText(String.format(
                "узлов=%d, листьев=%d, ключей=%d, высота=%d, средн.заполн.=%.2f, min=%d, max=%d",
                s.nodes(), s.leaves(), s.totalKeys(), s.height(),
                s.avgFill(), s.minKeys(), s.maxKeys()));
    }

    private void applyInitialDir(FileChooser chooser) {
        if (currentFile == null || currentFile.getParent() == null) return;
        File dir = currentFile.getParent().toFile();
        if (dir.isDirectory()) chooser.setInitialDirectory(dir);
    }

    private void runInBackground(Task<?> task, String message) {
        if (currentTask != null && currentTask.isRunning()) {
            Dialogs.showInfo(stage, "Операция", "Дождитесь завершения текущей операции");
            return;
        }
        currentTask = task;
        statusLabel.setText(message);

        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progressBar.setVisible(true);
        progressBar.setManaged(true);
        cancelButton.setVisible(true);
        cancelButton.setManaged(true);

        setIoButtonsDisabled(true);

        task.runningProperty().addListener((o, was, is) -> {
            if (Boolean.TRUE.equals(was) && Boolean.FALSE.equals(is)) {
                setIoButtonsDisabled(false);
                progressBar.setVisible(false);
                progressBar.setManaged(false);
                cancelButton.setVisible(false);
                cancelButton.setManaged(false);
                currentTask = null;
            }
        });

        Thread t = new Thread(task, "bg-task");
        t.setDaemon(true);
        t.start();
    }

    private void cancelCurrent() {
        if (currentTask != null && currentTask.isRunning()) {
            currentTask.cancel(true);
            statusLabel.setText("Отмена…");
        }
    }

    private void setIoButtonsDisabled(boolean d) {
        loadButton .setDisable(d);
        saveButton .setDisable(d);
        addButton  .setDisable(d);
        editButton .setDisable(d);
        delButton  .setDisable(d);
        genButton  .setDisable(d);
        benchButton.setDisable(d);
        clearButton.setDisable(d);
    }

    private void handleFailure(String action, Throwable ex) {
        if      (ex instanceof TreeOperationException te) Dialogs.showTreeError(stage, action, te);
        else if (ex instanceof CsvException ce)           Dialogs.showCsvError (stage, action, ce);
        else if (ex instanceof exceptions.AppException ae) Dialogs.showAppError(stage, action, ae);
        else Dialogs.showError(stage, action + " — ошибка",
                    ex == null ? "Неизвестная ошибка" : String.valueOf(ex.getMessage()));
    }

    private void showSkipped(List<CsvLoader.SkippedRow> skipped) {
        StringBuilder sb = new StringBuilder("Пропущено строк: ")
                .append(skipped.size()).append("\n\n");
        for (CsvLoader.SkippedRow s : skipped)
            sb.append("Строка ").append(s.lineNumber()).append(": ")
                    .append(s.cause().getMessage()).append('\n');
        Dialogs.showInfo(stage, "Пропущенные строки", sb.toString());
    }

    private static Integer parseOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return null; }
    }
}