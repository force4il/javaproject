package gui;

import csv.CsvLoader;
import exceptions.TreeOperationException;
import exceptions.TreeStateException;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import model.CatalogItem;
import model.Editable;
import model.Product;
import service.FileService;
import service.GeneratorService;
import service.TreeService;
import tree.BTree;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ProductController {

    private static final String TITLE_LOAD    = "Загрузка";
    private static final String TITLE_SAVE    = "Сохранение";
    private static final String TITLE_ADD     = "Добавление";
    private static final String TITLE_EDIT    = "Изменение";
    private static final String TITLE_DEL     = "Удаление";
    private static final String TITLE_GEN     = "Генерация";
    private static final String TITLE_SEARCH  = "Поиск";
    private static final String TITLE_RANGE   = "Диапазон";
    private static final String TITLE_CONFIRM = "Очистка дерева";

    private final Stage            stage;
    private final ProductView      view    = new ProductView();
    private final TreeService      tree    = new TreeService();
    private final FileService      files   = new FileService();
    private final GeneratorService generator = new GeneratorService();
    private final OperationSupport ops;

    private Path currentFile;

    public ProductController(Stage stage) {
        this.stage = stage;
        this.ops   = new OperationSupport(stage, view);
        ops.setOnIdle(this::refreshSelectionButtons);

        view.table.getSelectionModel().selectedItemProperty()
                .addListener((o, a, b) -> refreshSelectionButtons());

        view.loadButton.setOnAction(e -> onLoad());
        view.saveButton.setOnAction(e -> onSave());
        view.addButton.setOnAction(e -> onAdd());
        view.editButton.setOnAction(e -> onEdit());
        view.delButton.setOnAction(e -> onDelete());
        view.genButton.setOnAction(e -> onGenerate());
        view.benchButton.setOnAction(e -> BenchmarkDialog.show(stage));
        view.statsButton.setOnAction(e -> showNodeStats());
        view.raceDemoButton.setOnAction(e -> ConcurrentDemoDialog.show(stage));
        view.clearButton.setOnAction(e -> onClear());
        view.rangeButton.setOnAction(e -> onRange());
        view.showAllBtn.setOnAction(e -> refreshTable());
        view.searchField.setOnAction(e -> onSearch());
    }

    public BorderPane getView() { return view.root; }

    private void onLoad() {
        File file = InputDialogs.chooseOpenCsv(stage, currentFile);
        if (file == null) return;
        if (!tree.isEmpty() && !Dialogs.confirm(stage, TITLE_LOAD,
                "Текущее дерево будет очищено. Продолжить?")) return;

        Path path = file.toPath();
        Task<CsvLoader.LoadResult> task = files.loadTask(path);
        ops.run(task, "Загрузка…", result -> {
            try {
                // FileService только читает CSV; заполняем дерево после чтения.
                tree.clear();
                for (CatalogItem item : result.items()) {
                    tree.insertIfAbsent(item);
                }
                currentFile = path;
                refreshAll();
                ops.setStatus("Загружено: " + path.getFileName()
                        + " (пропущено строк: " + result.skippedRows().size() + ")");
                if (!result.skippedRows().isEmpty())
                    Dialogs.showSkipped(stage, result.skippedRows());
            } catch (TreeOperationException ex) {
                Dialogs.showTreeError(stage, TITLE_LOAD, ex);
            }
        }, TITLE_LOAD);
    }

    private void onSave() {
        File file = InputDialogs.chooseSaveCsv(stage, currentFile);
        if (file == null) return;
        Path path = file.toPath();
        List<CatalogItem> snapshot = new ArrayList<>(tree.all());

        Task<Void> task = files.saveTask(snapshot, path);
        ops.run(task, "Сохранение…", ignored -> {
            currentFile = path;
            ops.setStatus("Сохранено: " + path.getFileName());
        }, TITLE_SAVE);
    }

    private void onAdd() {
        ProductDialog d = new ProductDialog(stage, null, key -> keyExists(key));
        d.showAndWait().ifPresent(p -> {
            try {
                tree.insertIfAbsent(p);
                refreshAll();
                ops.setStatus("Добавлено: " + p.getItemNumber());
            } catch (TreeOperationException ex) {
                // сюда попадём только если insert упал по другой причине
                Dialogs.showTreeError(stage, TITLE_ADD, ex);
            }
        });
    }

    private boolean keyExists(int key) {
        try {
            return tree.search(key) != null;
        } catch (TreeOperationException e) {
            return false;   // ключ < 1 — сюда не попадём после validate()
        }
    }

    private void onEdit() {
        CatalogItem sel = view.table.getSelectionModel().getSelectedItem();
        if (!(sel instanceof Product existing)) return;
        ProductDialog d = new ProductDialog(stage, existing, this::keyExists);
        d.showAndWait().ifPresent(updated -> {
            try {
                tree.replace(existing.getItemNumber(), updated);
                refreshAll();
                ops.setStatus("Изменено: " + updated.getItemNumber());
            } catch (TreeOperationException ex) {
                Dialogs.showTreeError(stage, TITLE_EDIT, ex);
            }
        });
    }

    private void onDelete() {
        CatalogItem sel = view.table.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        try {
            tree.deleteIfExists(sel.getItemNumber());
            refreshAll();
            ops.setStatus("Удалено: " + sel.getItemNumber());
        } catch (TreeOperationException ex) {
            Dialogs.showTreeError(stage, TITLE_DEL, ex);
        }
    }

    private void onSearch() {
        String s = view.searchField.getText().trim();
        if (s.isEmpty()) return;
        int key;
        try { key = Integer.parseInt(s); }
        catch (NumberFormatException e) {
            Dialogs.showError(stage, TITLE_SEARCH,
                    InputDialogs.isNumericWithOptionalSign(s)
                            ? "Артикул не помещается в int"
                            : "Артикул должен быть целым числом");
            return;
        }
        CatalogItem found;
        try {
            found = tree.search(key);
        } catch (TreeOperationException ex) {
            Dialogs.showTreeError(stage, TITLE_SEARCH, ex);
            return;
        } catch (TreeStateException ex) {
            Dialogs.showError(stage, TITLE_SEARCH, ex.getMessage());
            return;
        }
        view.visualizer.redraw();
        updateStats();
        if (found == null) {
            ops.setStatus("Не найдено: " + key);
            view.table.getSelectionModel().clearSelection();
        } else {
            ops.setStatus("Найдено: " + key);
            view.table.getSelectionModel().select(found);
            view.table.scrollTo(found);
        }
    }

    private void onRange() {
        String fromText = view.rangeFrom.getText();
        String toText   = view.rangeTo.getText();
        Integer from = InputDialogs.parseOrNull(fromText);
        Integer to   = InputDialogs.parseOrNull(toText);

        if (from == null || to == null) {
            String msg = (InputDialogs.isBlank(fromText) || InputDialogs.isBlank(toText))
                    ? "Введите оба числа"
                    : (InputDialogs.isNumericWithOptionalSign(fromText.trim())
                    && InputDialogs.isNumericWithOptionalSign(toText.trim()))
                    ? "Одно из значений не помещается в int"
                    : "Введите оба числа";
            Dialogs.showError(stage, TITLE_RANGE, msg);
            return;
        }
        try {
            List<CatalogItem> res = tree.range(from, to);
            view.items.setAll(res);
            view.visualizer.redraw();
            updateStats();
            ops.setStatus("Диапазон [" + from + ".." + to + "]: " + res.size()
                    + " (в дереве " + tree.size() + ")");
        } catch (TreeOperationException ex) {
            Dialogs.showTreeError(stage, TITLE_RANGE, ex);
        }
    }

    private void onGenerate() {
        int count = InputDialogs.askGenCount(stage);
        if (count <= 0) return;
        long seed = System.nanoTime();

        Task<Integer> task = new Task<>() {
            @Override protected Integer call() {
                return generator.generate(tree, count, seed,
                        i -> updateProgress(i, count), this::isCancelled);
            }
        };
        ops.run(task, "Генерация…", inserted -> {
            refreshAll();
            ops.setStatus("Сгенерировано: " + inserted + " (seed=" + seed + ")");
        }, TITLE_GEN);
    }

    private void onClear() {
        if (tree.isEmpty()) return;
        if (!Dialogs.confirm(stage, TITLE_CONFIRM,
                "Текущее дерево будет очищено. Продолжить?")) return;
        tree.clear();
        refreshAll();
        ops.setStatus("Очищено");
    }

    private void refreshAll() {
        refreshTable();
        view.visualizer.setTree(tree.raw());
        updateStats();
    }

    private void refreshTable() {
        view.items.setAll(tree.all());
    }

    private void showNodeStats() {
        try {
            BTree.FillStats s = tree.stats();
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.initOwner(stage);
            alert.setTitle("Статистика B-дерева");
            alert.setHeaderText("Заполненность узлов");
            alert.setContentText(String.format(java.util.Locale.forLanguageTag("ru-RU"),
                    "Узлов: %d%nЛистьев: %d%nКлючей: %d%nВысота: %d%n" +
                            "Средняя заполненность: %.2f%nМинимум ключей: %d%nМаксимум ключей: %d",
                    s.nodes(), s.leaves(), s.totalKeys(), s.height(),
                    s.avgFill(), s.minKeys(), s.maxKeys()));
            alert.showAndWait();
        } catch (RuntimeException ex) {
            Dialogs.showError(stage, "Ошибка статистики", String.valueOf(ex));
        }
    }

    private void updateStats() {
        BTree.FillStats s = tree.stats();
        ops.setStats(String.format(
                "узлов=%d, листьев=%d, ключей=%d, высота=%d, средн.заполн.=%.2f, min=%d, max=%d",
                s.nodes(), s.leaves(), s.totalKeys(), s.height(),
                s.avgFill(), s.minKeys(), s.maxKeys()));
    }

    private void refreshSelectionButtons() {
        CatalogItem sel = view.table.getSelectionModel().getSelectedItem();
        view.editButton.setDisable(!(sel instanceof Editable));
        view.delButton .setDisable(sel == null);
    }
}