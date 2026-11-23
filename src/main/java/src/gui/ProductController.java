package src.gui;

import src.csv.CsvLoader;
import src.csv.CsvSchema;
import src.exceptions.TreeOperationException;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import src.model.CatalogItem;
import src.model.Editable;
import src.model.Product;
import src.server.CatalogService;
import src.service.FileService;
import src.service.GeneratorService;
import src.service.TreeService;
import src.tree.BTree;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import src.service.SerializationService;
import src.serializable.*;
import javafx.stage.FileChooser;


public final class ProductController {

    private static final String TITLE_LOAD = "Загрузка";
    private static final String TITLE_SAVE = "Сохранение";
    private static final String TITLE_ADD = "Добавление";
    private static final String TITLE_EDIT = "Изменение";
    private static final String TITLE_DEL = "Удаление";
    private static final String TITLE_GEN = "Генерация";
    private static final String TITLE_SEARCH = "Поиск";
    private static final String TITLE_RANGE = "Диапазон";
    private static final String TITLE_CONFIRM = "Очистка дерева";

    private final Stage stage;
    private final ProductView view = new ProductView();
    private final FileService files = new FileService();
    private final SerializationService serialization = new SerializationService();
    private final GeneratorService generator = new GeneratorService();
    private final OperationSupport ops;
    private final CatalogService catalogService;

    private Path currentFile;

    public ProductController(Stage stage, CatalogService service) {
        this.stage = stage;
        this.catalogService = service;
        this.ops = new OperationSupport(stage, view);
        ops.setOnIdle(() -> {
            refreshSelectionButtons();
            refreshExportButton();
        });
        view.table.getSelectionModel().selectedItemProperty()
                .addListener((o, a, b) -> refreshSelectionButtons());

        view.loadButton.setOnAction(e -> onLoad());
        view.saveButton.setOnAction(e -> onSave());
        view.importButton.setOnAction(e -> onImport());
        view.exportButton.setOnAction(e -> onExport());
        view.compareFormatsButton.setOnAction(e -> FormatBenchmarkDialog.show(stage, new ArrayList<>(catalogService.findAll())));
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

        refreshExportButton();
    }

    public BorderPane getView() { return view.root; }

    private void onLoad() {
        File file = InputDialogs.chooseOpenCsv(stage, currentFile);
        if (file == null)
            return;

        if (!catalogService.isEmpty() && !Dialogs.confirm(stage, TITLE_LOAD, "Текущее дерево будет очищено. Продолжить?"))
            return;

        Path path = file.toPath();
        Task<CsvLoader.LoadResult> task = files.loadTask(path);

        ops.run(task, "Загрузка…", result -> {
            try {
                boolean migrate = result.sourceVersion() < CatalogVersions.CURRENT;
                if (migrate && !Dialogs.confirm(stage, "Миграция CSV",
                        "Файл версии " + result.sourceVersion()
                                + ". Мигрировать до версии "
                                + CatalogVersions.CURRENT + "?")) {
                    ops.setStatus("Загрузка отменена");
                    return;
                }
                catalogService.replaceAll(result.items());

                currentFile = path;
                refreshAll();
                String mig = migrate ? " (миграция v" + result.sourceVersion()
                        + " → v" + CatalogVersions.CURRENT + ")" : "";
                ops.setStatus("Загружено: " + path.getFileName() + mig
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
        if (file == null)
            return;

        Integer version = InputDialogs.chooseVersion(stage, CsvSchema.CURRENT_VERSION, "Версия CSV");
        if (version == null)
            return;

        if (version < CsvSchema.CURRENT_VERSION) {
            String loss = CatalogVersions.lossFromTo(CsvSchema.CURRENT_VERSION, version);
            if (!Dialogs.confirm(stage, "Потеря данных",
                    "Сохранение в CSV v" + version + " приведёт к потере полей:\n"
                            + loss + "\nПродолжить?")) return;
        }
        Path path = file.toPath();
        List<CatalogItem> snapshot = new ArrayList<>(catalogService.findAll());
        ops.run(files.saveTask(snapshot, path, version), "Сохранение…", ignored -> {
            currentFile = path;
            ops.setStatus("Сохранено: " + path.getFileName() + " (v" + version + ")");
        }, TITLE_SAVE);
    }

    private FileChooser serialChooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("JSON (*.json)", "*.json"),
                new FileChooser.ExtensionFilter("BIN (*.bin)", "*.bin")
        );
        return chooser;
    }

    private Serializer<List<CatalogItem>> serializerFor(File file) {
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".json"))
            return new JsonCatalogSerializer();

        if (name.endsWith(".bin"))
            return new BinaryCatalogSerializer();

        throw new IllegalArgumentException(
                "Выберите файл .json или .bin");
    }

    private void onExport() {
        FileChooser chooser = serialChooser("Экспорт каталога");
        chooser.setInitialFileName("catalog.json");
        File file = chooser.showSaveDialog(stage);
        if (file == null)
            return;

        final Serializer<List<CatalogItem>> serializer;
        try {
            serializer = serializerFor(file);
        } catch (IllegalArgumentException ex) {
            Dialogs.showError(stage, "Экспорт", ex.getMessage());
            return;
        }

        Integer version = InputDialogs.chooseVersion(stage, serializer.currentVersion(), "Версия экспорта");
        if (version == null)
            return;

        if (version < serializer.currentVersion()) {
            String loss = serializer.describeDowngradeLoss(version);
            if (!Dialogs.confirm(stage, "Потеря данных",
                    "Экспорт в v" + version + " приведёт к потере полей:\n" + loss + "\nПродолжить?"))
                return;
        }

        var snapshot = new ArrayList<>(catalogService.findAll());
        ops.run(serialization.saveTask(serializer, snapshot, file.toPath(), version), "Экспорт…",
                ignored -> ops.setStatus("Экспортировано: " + file.getName() + " (v" + version + ")"),
                "Экспорт");
    }

    private void onImport() {
        File file = serialChooser("Импорт каталога").showOpenDialog(stage);
        if (file == null)
            return;

        final Serializer<List<CatalogItem>> serializer;
        try {
            serializer = serializerFor(file);
        } catch (IllegalArgumentException ex) {
            Dialogs.showError(stage, "Импорт", ex.getMessage());
            return;
        }

        ops.run(serialization.loadTask(serializer, file.toPath()), "Импорт…",
                result -> {
                    boolean migrate = result.sourceVersion() != result.targetVersion();
                    if (!catalogService.isEmpty() || migrate) {
                        StringBuilder msg = new StringBuilder();
                        msg.append("Файл: ").append(file.getName()).append("\n")
                                .append("Формат: ").append(serializer.format()).append("\n")
                                .append("Версия в файле: v").append(result.sourceVersion()).append("\n")
                                .append("Записей: ").append(result.data().size()).append("\n\n");
                        if (!catalogService.isEmpty())
                            msg.append("Текущее дерево будет заменено.\n");
                        if (migrate)
                            msg.append("Мигрировать до версии ")
                                    .append(result.targetVersion()).append("?");
                        if (!Dialogs.confirm(stage, "Импорт", msg.toString())) {
                            ops.setStatus("Импорт отменён");
                            return;
                        }
                    }
                    try {
                        catalogService.replaceAll(result.data());
                        refreshAll();
                        String mig = migrate
                                ? " (миграция v" + result.sourceVersion()
                                + " → v" + result.targetVersion() + ")" : "";
                        ops.setStatus("Импортировано: " + result.data().size() + " записей" + mig);
                    } catch (TreeOperationException ex) {
                        Dialogs.showTreeError(stage, "Импорт", ex);
                    }
                }, "Импорт");
    }

    private void onAdd() {
        ProductDialog d = new ProductDialog(stage, null, this::keyExists);
        d.showAndWait().ifPresent(p -> {
            try {
                boolean added = catalogService.createIfAbsent(p);
                refreshAll();
                if (added)
                    ops.setStatus("Добавлено: " + p.getItemNumber());
                else
                    ops.setStatus("Артикул " + p.getItemNumber() + " уже существует");
            } catch (TreeOperationException ex) {
                Dialogs.showTreeError(stage, TITLE_ADD, ex);
            }
        });
    }

    private boolean keyExists(int key) {
        return catalogService.findOrNull(key) != null;
    }

    private void onEdit() {
        CatalogItem sel = view.table.getSelectionModel().getSelectedItem();
        if (!(sel instanceof Product existing))
            return;

        ProductDialog d = new ProductDialog(stage, existing, this::keyExists);
        d.showAndWait().ifPresent(updated -> {
            try {
                catalogService.replace(existing.getItemNumber(), updated);
                refreshAll();
                ops.setStatus("Изменено: " + updated.getItemNumber());
            } catch (TreeOperationException ex) {
                Dialogs.showTreeError(stage, TITLE_EDIT, ex);
            }
        });
    }

    private void onDelete() {
        CatalogItem sel = view.table.getSelectionModel().getSelectedItem();
        if (sel == null)
            return;

        try {
            catalogService.deleteIfExists(sel.getItemNumber());
            refreshAll();
            ops.setStatus("Удалено: " + sel.getItemNumber());
        } catch (TreeOperationException ex) {
            Dialogs.showTreeError(stage, TITLE_DEL, ex);
        }
    }

    private void onSearch() {
        String s = view.searchField.getText().trim();
        if (s.isEmpty())
            return;

        int key;
        try { key = Integer.parseInt(s); }
        catch (NumberFormatException e) {
            Dialogs.showError(stage, TITLE_SEARCH,
                    InputDialogs.isNumericWithOptionalSign(s) ? "Артикул не помещается в int"
                            : "Артикул должен быть целым числом");
            return;
        }
        CatalogItem found;
        found = catalogService.findOrNull(key);
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
        String toText = view.rangeTo.getText();
        Integer from = InputDialogs.parseOrNull(fromText);
        Integer to = InputDialogs.parseOrNull(toText);

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
            List<CatalogItem> res = catalogService.range(from, to);
            view.items.setAll(res);
            view.visualizer.redraw();
            updateStats();
            ops.setStatus("Диапазон [" + from + ".." + to + "]: " + res.size()
                    + " (в дереве " + catalogService.size() + ")");
        } catch (TreeOperationException ex) {
            Dialogs.showTreeError(stage, TITLE_RANGE, ex);
        }
    }

    private void onGenerate() {
        int count = InputDialogs.askGenCount(stage);
        if (count <= 0)
            return;

        long seed = System.nanoTime();
        final List<CatalogItem> initialItems = new ArrayList<>(catalogService.findAll());
        Task<List<CatalogItem>> task = new Task<>() {
            @Override
            protected List<CatalogItem> call() throws Exception {
                TreeService temporary = new TreeService();
                for (CatalogItem item : initialItems)
                    temporary.insertIfAbsent(item);

                generator.generate(temporary, count, seed,
                        i -> updateProgress(i, count), this::isCancelled);

                if (isCancelled())
                    throw new java.util.concurrent.CancellationException();

                return temporary.all();
            }
        };

        ops.run(task, "Генерация…", resultList -> {
            try {
                catalogService.replaceAll(resultList);
                refreshAll();
                ops.setStatus("Сгенерировано: " + count + " (seed=" + seed + ")");
            } catch (TreeOperationException ex) {
                Dialogs.showTreeError(stage, TITLE_GEN, ex);
            }
        }, TITLE_GEN);
    }

    private void onClear() {
        if (catalogService.isEmpty())
            return;

        if (!Dialogs.confirm(stage, TITLE_CONFIRM, "Текущее дерево будет очищено. Продолжить?"))
            return;

        catalogService.clear();
        refreshAll();
        ops.setStatus("Очищено");
    }

    private void refreshAll() {
        refreshTable();
        view.visualizer.setTree(catalogService.raw());
        updateStats();
        refreshExportButton();
    }

    private void refreshTable() {
        view.items.setAll(catalogService.findAll());
    }

    private void showNodeStats() {
        try {
            BTree.FillStats s = catalogService.stats();
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
        BTree.FillStats s = catalogService.stats();
        ops.setStats(String.format("узлов=%d, листьев=%d, ключей=%d, высота=%d, средн.заполн.=%.2f, min=%d, max=%d",
                s.nodes(), s.leaves(), s.totalKeys(), s.height(), s.avgFill(), s.minKeys(), s.maxKeys()));
    }

    private void refreshSelectionButtons() {
        CatalogItem sel = view.table.getSelectionModel().getSelectedItem();
        view.editButton.setDisable(!(sel instanceof Editable));
        view.delButton.setDisable(sel == null);
    }

    private void refreshExportButton() {
        view.exportButton.setDisable(catalogService.isEmpty());
    }
}