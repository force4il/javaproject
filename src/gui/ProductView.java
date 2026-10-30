package gui;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import model.CatalogItem;
import model.ProductWithWarranty;

import java.util.function.Function;

public final class ProductView {

    private static final double SPLIT_DIVIDER      = 0.55;
    private static final double TOOLBAR_VIEWPORT_H = 96;
    private static final double TOOLBAR_MAX_H      = 160;
    private static final double PROGRESS_BAR_WIDTH = 180;
    private static final double TOOLBAR_HGAP       = 8;
    private static final double TOOLBAR_VGAP       = 8;
    private static final double TOOLBAR_PADDING    = 10;
    private static final double STATS_PADDING      = 6;

    private static final int SEARCH_COLUMNS  = 10;
    private static final int RANGE_COLUMNS   = 6;

    public final BorderPane root = new BorderPane();

    public final ObservableList<CatalogItem> items = FXCollections.observableArrayList();
    public final TableView<CatalogItem>      table;
    public final BTreeVisual                 visualizer = new BTreeVisual();

    public final Button loadButton   = new Button("Загрузить CSV");
    public final Button saveButton   = new Button("Сохранить CSV");
    public final Button addButton    = new Button("Добавить");
    public final Button editButton   = new Button("Изменить");
    public final Button delButton    = new Button("Удалить");
    public final Button genButton    = new Button("Генерировать…");
    public final Button benchButton  = new Button("Замеры…");
    public final Button statsButton  = new Button("Статистика узлов");
    public final Button clearButton  = new Button("Очистить");
    public final Button cancelButton = new Button("Отмена");
    public final Button raceDemoButton = new Button("Демо гонки…");

    public final TextField searchField = new TextField();
    public final TextField rangeFrom   = new TextField();
    public final TextField rangeTo     = new TextField();
    public final Button    rangeButton = new Button("Диапазон");
    public final Button    showAllBtn  = new Button("Показать всё");

    public final Label       statusLabel = new Label("");
    public final Label       statsLabel  = new Label();
    public final ProgressBar progressBar = new ProgressBar();

    public ProductView() {
        table = buildTable();

        editButton.setDisable(true);
        delButton.setDisable(true);

        searchField.setPromptText("Артикул");
        searchField.setPrefColumnCount(SEARCH_COLUMNS);
        rangeFrom.setPromptText("от");
        rangeTo.setPromptText("до");
        rangeFrom.setPrefColumnCount(RANGE_COLUMNS);
        rangeTo.setPrefColumnCount(RANGE_COLUMNS);

        progressBar.setPrefWidth(PROGRESS_BAR_WIDTH);
        progressBar.setVisible(false);
        progressBar.setManaged(false);
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);

        root.setTop(new VBox(buildToolbar(), buildStatsBar()));
        root.setCenter(buildSplit());
    }


    private TableView<CatalogItem> buildTable() {
        TableView<CatalogItem> tv = new TableView<>(items);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tv.getColumns().add(column("Тип",         i -> i.getType().getCsvName()));
        tv.getColumns().add(column("Артикул",     i -> String.valueOf(i.getItemNumber())));
        tv.getColumns().add(column("Название",    CatalogItem::getProductName));
        tv.getColumns().add(column("Категория",   CatalogItem::getCategory));
        tv.getColumns().add(column("Цена",        i -> String.valueOf(i.getPrice())));
        tv.getColumns().add(column("Остаток",     i -> String.valueOf(i.getRemainder())));
        tv.getColumns().add(column("Гарантия до", ProductView::warrantyEndOf));
        return tv;
    }

    private static TableColumn<CatalogItem, String> column(
            String title, Function<CatalogItem, String> extractor) {
        TableColumn<CatalogItem, String> col = new TableColumn<>(title);
        col.setCellValueFactory(d -> new ReadOnlyStringWrapper(extractor.apply(d.getValue())));
        return col;
    }

    private static String warrantyEndOf(CatalogItem item) {
        if (item instanceof ProductWithWarranty pw && pw.getStartOfWarranty() != null) {
            var end = pw.getWarrantyEndDate();
            return end == null ? "" : end.toString();
        }
        return "";
    }


    private ScrollPane buildToolbar() {
        // Первая строчка панели
        FlowPane actionsRow = new FlowPane(TOOLBAR_HGAP, TOOLBAR_VGAP);
        actionsRow.setAlignment(Pos.CENTER_LEFT);
        actionsRow.getChildren().addAll(
                loadButton, saveButton, addButton, editButton, delButton,
                new Separator(Orientation.VERTICAL),
                genButton, benchButton, statsButton, raceDemoButton, clearButton);
        // Вторая строчка панели
        FlowPane searchRow = new FlowPane(TOOLBAR_HGAP, TOOLBAR_VGAP);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        searchRow.getChildren().addAll(
                new Label("Поиск:"), searchField,
                new Label("Диапазон:"), rangeFrom, rangeTo, rangeButton,
                showAllBtn,
                new Separator(Orientation.VERTICAL),
                cancelButton, progressBar, statusLabel);

        VBox toolbar = new VBox(TOOLBAR_VGAP, actionsRow, searchRow);
        toolbar.setPadding(new Insets(TOOLBAR_PADDING));

        ScrollPane scroll = new ScrollPane(toolbar);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setPrefViewportHeight(132);
        scroll.setMaxHeight(200);
        scroll.setStyle("-fx-background-color: transparent;");
        return scroll;
    }

    private HBox buildStatsBar() {
        HBox bar = new HBox(8, new Label("Статистика:"), statsLabel);
        bar.setPadding(new Insets(STATS_PADDING, 10, STATS_PADDING, 10));
        bar.setStyle("-fx-background-color: #eef2f7;");
        return bar;
    }

    private SplitPane buildSplit() {
        SplitPane split = new SplitPane(table, visualizer);
        split.setDividerPositions(SPLIT_DIVIDER);
        SplitPane.setResizableWithParent(visualizer, true);
        return split;
    }
}