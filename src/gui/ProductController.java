package gui;

import csv.CsvLoader;
import csv.CsvSave;
import exceptions.CsvException;
import model.CatalogItem;
import model.Editable;
import model.Product;
import model.ProductWithWarranty;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Function;

public final class ProductController {

    private static final String CSV_EXT = "*.csv";

    private static final String TITLE_LOAD = "Загрузка";
    private static final String TITLE_SAVE = "Сохранение";
    private static final String TITLE_EDIT = "Изменение";

    private static final double PROGRESS_BAR_WIDTH = 220.0;

    private final Stage stage;
    private final BorderPane root;
    private final TableView<CatalogItem> table;
    private final ObservableList<CatalogItem> items = FXCollections.observableArrayList();

    private final Button loadButton = new Button("Загрузить из CSV");
    private final Button saveButton = new Button("Сохранить в CSV");
    private final Button addButton  = new Button("Добавить");
    private final Button editButton = new Button("Изменить");
    private final Label  statusLabel = new Label("Файл не выбран");
    private final ProgressBar progressBar = new ProgressBar();

    private Path currentFile;

    public ProductController(Stage stage) {
        this.stage = stage;
        this.table = buildTable();

        editButton.setDisable(true);
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) ->
                editButton.setDisable(!(sel instanceof Editable)));

        loadButton.setOnAction(e -> onLoad());
        saveButton.setOnAction(e -> onSave());
        addButton.setOnAction(e -> onAdd());
        editButton.setOnAction(e -> onEdit());

        progressBar.setPrefWidth(PROGRESS_BAR_WIDTH);
        progressBar.setVisible(false);
        progressBar.setManaged(false);

        HBox toolbar = new HBox(8,
                loadButton, saveButton, addButton, editButton,
                statusLabel, progressBar);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(10));
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        statusLabel.setMaxWidth(Double.MAX_VALUE);

        this.root = new BorderPane();
        root.setTop(toolbar);
        root.setCenter(table);
    }

    public BorderPane getView() { return root; }

    // Таблица
    private TableView<CatalogItem> buildTable() {
        TableView<CatalogItem> tv = new TableView<>(items);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tv.getColumns().add(column("Тип",       item -> item.getType().getCsvName()));
        tv.getColumns().add(column("Артикул",   item -> String.valueOf(item.getItemNumber())));
        tv.getColumns().add(column("Название",  CatalogItem::getProductName));
        tv.getColumns().add(column("Категория", CatalogItem::getCategory));
        tv.getColumns().add(column("Цена",      item -> String.valueOf(item.getPrice())));
        tv.getColumns().add(column("Остаток",   item -> String.valueOf(item.getRemainder())));
        tv.getColumns().add(column("Гарантия до", this::warrantyEndOf));
        return tv;
    }

    private static TableColumn<CatalogItem, String> column(String title,
                                                           Function<CatalogItem, String> extractor) {
        TableColumn<CatalogItem, String> col = new TableColumn<>(title);
        col.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(extractor.apply(data.getValue())));
        return col;
    }

    private String warrantyEndOf(CatalogItem item) {
        if (item instanceof ProductWithWarranty pw && pw.getStartOfWarranty() != null) {
            return pw.getWarrantyEndDate().toString();
        }
        return "";
    }

    // Действия
    private void onLoad() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Открыть CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", CSV_EXT));
        applyInitialDir(chooser);

        File file = chooser.showOpenDialog(stage);
        if (file == null) return;
        Path path = file.toPath();

        Task<CsvLoader.LoadResult> task = new Task<>() {
            @Override
            protected CsvLoader.LoadResult call() throws Exception {
                return new CsvLoader().load(path);
            }
        };

        task.setOnSucceeded(e -> {
            CsvLoader.LoadResult r = task.getValue();
            items.setAll(r.items());
            currentFile = path;
            statusLabel.setText("Загружено: " + path.getFileName()
                    + " (пропущено строк: " + r.skippedRows().size() + ")");
            if (!r.skippedRows().isEmpty())
                showSkipped(r.skippedRows());
        });

        task.setOnFailed(e -> {
            statusLabel.setText("Ошибка загрузки: " + path.getFileName());
            handleCsvFailure(TITLE_LOAD, task.getException());
        });

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

        var snapshot = new ArrayList<CatalogItem>(items);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                CsvSave.save(snapshot, path);
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            currentFile = path;
            statusLabel.setText("Сохранено: " + path.getFileName());
        });

        task.setOnFailed(e -> {
            statusLabel.setText("Ошибка сохранения: " + path.getFileName());
            handleCsvFailure(TITLE_SAVE, task.getException());
        });

        runInBackground(task, "Сохранение…");
    }

    private void onAdd() {
        ProductDialog dialog = new ProductDialog(stage, null);
        Optional<Product> result = dialog.showAndWait();
        result.ifPresent(p -> {
            items.add(p);
            table.getSelectionModel().select(p);
        });
    }

    private void onEdit() {
        CatalogItem selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        if (!(selected instanceof Product existing)) {
            Dialogs.showInfo(stage, TITLE_EDIT,
                    "Этот тип записи доступен только для чтения и не может быть изменён.");
            return;
        }

        ProductDialog dialog = new ProductDialog(stage, existing);
        dialog.showAndWait().ifPresent(updated -> {
            existing.copyFrom(updated);
            table.refresh();
        });
    }

    private void applyInitialDir(FileChooser chooser) {
        if (currentFile == null || currentFile.getParent() == null) return;
        File dir = currentFile.getParent().toFile();
        if (dir.isDirectory())
            chooser.setInitialDirectory(dir);
    }

    private void runInBackground(Task<?> task, String message) {
        statusLabel.setText(message);
        setIoButtonsDisabled(true);

        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progressBar.setVisible(true);
        progressBar.setManaged(true);

        task.runningProperty().addListener((obs, was, is) -> {
            if (Boolean.TRUE.equals(was) && Boolean.FALSE.equals(is)) {
                setIoButtonsDisabled(false);
                progressBar.setVisible(false);
                progressBar.setManaged(false);
            }
        });

        Thread t = new Thread(task, "csv-io");
        t.setDaemon(true);
        t.start();
    }

    private void setIoButtonsDisabled(boolean disabled) {
        loadButton.setDisable(disabled);
        saveButton.setDisable(disabled);
        addButton.setDisable(disabled);
    }

    private void handleCsvFailure(String action, Throwable ex) {
        if (ex instanceof CsvException ce) {
            Dialogs.showCsvError(stage, action, ce);
        } else {
            Dialogs.showError(stage, action + " — ошибка",
                    ex == null ? "Неизвестная ошибка" : String.valueOf(ex.getMessage()));
        }
    }

    private void showSkipped(java.util.List<CsvLoader.SkippedRow> skipped) {
        StringBuilder sb = new StringBuilder();
        sb.append("Пропущено строк: ").append(skipped.size()).append("\n\n");
        for (CsvLoader.SkippedRow s : skipped) {
            sb.append("Строка ").append(s.lineNumber())
                    .append(": ").append(s.cause().getMessage()).append('\n');
        }
        Dialogs.showInfo(stage, "Пропущенные строки", sb.toString());
    }
}