package gui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import tree.BTree;

/** Canvas-панель, рисует B-дерево */
public final class BTreeVisual extends Pane {

    private static final double DX = 56;
    private static final double DY = 90;
    private static final double KEY_W = 44;
    private static final double KEY_H = 30;
    private static final double PAD = 30;

    private static final Color NODE_FILL = Color.web("#f5f7fa");
    private static final Color NODE_STROKE = Color.web("#4a5568");
    private static final Color KEY_STROKE = Color.web("#a0aec0");
    private static final Color EDGE_COLOR = Color.web("#718096");
    private static final Color HL_FILL = Color.web("#ffe066");
    private static final Color HL_STROKE = Color.web("#f59f00");
    private static final Color TEXT_COLOR = Color.web("#1a202c");

    private final Canvas canvas = new Canvas();
    private BTree<?> tree;
    private Integer highlightedKey;

    public BTreeVisual() {
        getChildren().add(canvas);
        setMinSize(400, 320);
        widthProperty().addListener((o, a, b) -> redraw());
        heightProperty().addListener((o, a, b) -> redraw());
    }

    @Override
    protected void layoutChildren() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
        redraw();
    }

    private Throwable renderError;
    private String   renderDiagnostics;

    public void setTree(BTree<?> tree) {
        this.tree = tree;
        this.renderError = null;
        this.renderDiagnostics = null;
        redraw();
    }

    // true, если при последней попытке нарисовать было исключение
    public boolean isCorrupted() {
        return renderError != null;
    }

    public void redraw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        g.setFill(Color.WHITE);
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        if (tree == null) {
            drawPlain(g, "Дерево не задано");
            return;
        }

        if (renderError != null) {
            drawCorrupted(g);
            return;
        }

        // Проверяем и size, и реальные ключи в корне
        if (tree.isEmpty() && tree.getRoot().getNumKeys() == 0) {
            drawPlain(g, "Дерево пусто");
            return;
        }

        try {
            tree.computeLayout();
            for (BTree.Node n : tree.getLastVisited())
                n.setHighlighted(true);

            try {
                double[] b = {Double.MAX_VALUE, -Double.MAX_VALUE, 0};
                collectBounds(tree.getRoot(), b);
                double treeW = (b[1] - b[0]) * DX + KEY_W;
                double treeH = b[2] * DY + KEY_H;
                double availW = Math.max(100, canvas.getWidth()  - 2 * PAD);
                double availH = Math.max(100, canvas.getHeight() - 2 * PAD);
                double scale = Math.min(availW / treeW, availH / treeH);
                if (scale > 1.0)
                    scale = 1.0;

                if (scale < 0.05)
                    scale = 0.05;

                double offX = (canvas.getWidth() - treeW * scale) / 2.0 - b[0] * DX * scale;
                double offY = PAD;

                g.setLineWidth(Math.max(1.0, scale * 1.4));
                drawEdges(g, tree.getRoot(), scale, offX, offY);
                drawNodes(g, tree.getRoot(), scale, offX, offY);
            } finally {
                for (BTree.Node n : tree.getLastVisited())
                    n.setHighlighted(false);
            }
        } catch (Throwable ex) {
            renderError = ex;
            renderDiagnostics = buildDiagnostics();
            drawCorrupted(g);
        }
    }

    private String buildDiagnostics() {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("size=").append(tree.size());
        } catch (Throwable t) {
            sb.append("size=?");
        }

        try {
            BTree.Node r = tree.getRoot();
            sb.append(", root.numKeys=").append(r.getNumKeys())
                    .append(", root.leaf=").append(r.isLeaf());
        } catch (Throwable t) {
            sb.append(", root=?");
        }

        return sb.toString();
    }

    private void drawPlain(GraphicsContext g, String text) {
        g.setFill(Color.GRAY);
        g.setFont(Font.font(14));
        g.setTextAlign(TextAlignment.LEFT);
        g.fillText(text, 20, 30);
    }

    private void drawCorrupted(GraphicsContext g) {
        double y = 30;
        g.setTextAlign(TextAlignment.LEFT);

        g.setFill(Color.web("#c53030"));
        g.setFont(Font.font(14));
        g.fillText("Дерево повреждено — визуализация невозможна", 20, y);
        y += 25;

        g.setFill(Color.web("#2d3748"));
        g.setFont(Font.font(12));
        if (renderDiagnostics != null && !renderDiagnostics.isEmpty()) {
            g.fillText("Диагностика: " + renderDiagnostics, 20, y);
            y += 20;
        }
        g.fillText("Ошибка: " + renderError.getClass().getSimpleName()
                        + (renderError.getMessage() != null
                        ? ": " + renderError.getMessage() : ""),
                20, y);
        y += 25;
        g.fillText("Нажмите «Очистить», чтобы восстановить дерево.", 20, y);
    }

    private void collectBounds(BTree.Node n, double[] b) {
        for (int i = 0; i < n.getNumKeys(); i++) {
            b[0] = Math.min(b[0], n.getKeyX(i));
            b[1] = Math.max(b[1], n.getKeyX(i));
        }
        b[2] = Math.max(b[2], n.getLayoutY());
        if (!n.isLeaf())
            for (int i = 0; i <= n.getNumKeys(); i++)
                collectBounds(n.getChild(i), b);
    }

    private double xKey(double keyIdx, double scale, double offX) {
        return keyIdx * DX * scale + offX;
    }
    private double yLevel(double depth, double scale, double offY) {
        return depth * DY * scale + offY;
    }

    private double nodeCenterX(BTree.Node n, double scale, double offX) {
        if (n.getNumKeys() == 0)
            return xKey(n.getLayoutX(), scale, offX);

        double first = n.getKeyX(0);
        double last  = n.getKeyX(n.getNumKeys() - 1);
        return xKey((first + last) / 2.0, scale, offX);
    }

    private void drawEdges(GraphicsContext g, BTree.Node n, double scale, double offX, double offY) {
        if (n.isLeaf())
            return;

        g.setStroke(EDGE_COLOR);
        double px = nodeCenterX(n, scale, offX);
        double py = yLevel(n.getLayoutY(), scale, offY) + KEY_H * scale;
        for (int i = 0; i <= n.getNumKeys(); i++) {
            BTree.Node c = n.getChild(i);
            if (c == null)
                continue;

            double cx = nodeCenterX(c, scale, offX);
            double cy = yLevel(c.getLayoutY(), scale, offY);
            g.strokeLine(px, py, cx, cy);
            drawEdges(g, c, scale, offX, offY);
        }
    }

    private void drawNodes(GraphicsContext g, BTree.Node n,
                           double scale, double offX, double offY) {
        boolean highlighted = n.isHighlighted();
        if (n.getNumKeys() == 0) {
            double x0 = xKey(n.getLayoutX(), scale, offX) - KEY_W * scale / 2;
            double y0 = yLevel(n.getLayoutY(), scale, offY);
            double w = KEY_W * scale;
            double h = KEY_H * scale;

            g.setFill(highlighted ? HL_FILL : NODE_FILL);
            g.setStroke(highlighted ? HL_STROKE : NODE_STROKE);
            g.setLineWidth(highlighted ? 2.5 : 1.5);
            g.fillRoundRect(x0, y0, w, h, 8 * scale, 8 * scale);
            g.strokeRoundRect(x0, y0, w, h, 8 * scale, 8 * scale);
            return;
        }

        // Считаем координаты центров первого и последнего ключей
        double firstCenter = xKey(n.getKeyX(0), scale, offX);
        double lastCenter = xKey(n.getKeyX(n.getNumKeys() - 1), scale, offX);

        // Левый край узла и его ширина
        double x0 = firstCenter - KEY_W * scale / 2;
        double w = lastCenter - firstCenter + KEY_W * scale;
        double y0 = yLevel(n.getLayoutY(), scale, offY);
        double h = KEY_H * scale;

        // Фон узла
        g.setFill(highlighted ? HL_FILL : NODE_FILL);
        g.setStroke(highlighted ? HL_STROKE : NODE_STROKE);
        g.setLineWidth(highlighted ? 2.5 : 1.5);
        g.fillRoundRect(x0, y0, w, h, 8 * scale, 8 * scale);
        g.strokeRoundRect(x0, y0, w, h, 8 * scale, 8 * scale);

        // посередине между ключами
        g.setStroke(KEY_STROKE);
        g.setLineWidth(1.0);
        for (int i = 1; i < n.getNumKeys(); i++) {
            double prevCenter = xKey(n.getKeyX(i - 1), scale, offX);
            double currCenter = xKey(n.getKeyX(i), scale, offX);
            double mid = (prevCenter + currCenter) / 2.0;
            g.strokeLine(mid, y0, mid, y0 + h);
        }

        // Подсветка конкретного вставленного ключа
        for (int i = 0; i < n.getNumKeys(); i++) {
            if (highlightedKey != null && java.util.Objects.equals(n.getKey(i), highlightedKey)) {
                double cx = xKey(n.getKeyX(i), scale, offX);
                double pad = 2 * scale;
                g.setFill(HL_FILL);
                g.fillRect(cx - KEY_W * scale / 2 + pad, y0 + pad,
                        KEY_W * scale - 2 * pad, h - 2 * pad);
                g.setStroke(HL_STROKE);
                g.setLineWidth(2 * scale);
                g.strokeRect(cx - KEY_W * scale / 2 + pad, y0 + pad,
                        KEY_W * scale - 2 * pad, h - 2 * pad);
            }
        }

        // Текст ключей
        g.setFill(TEXT_COLOR);
        g.setFont(Font.font(Math.max(9, 13 * scale)));
        g.setTextAlign(TextAlignment.CENTER);
        double cy = y0 + h / 2 + Math.max(4, 5 * scale);

        for (int i = 0; i < n.getNumKeys(); i++) {
            double cx = xKey(n.getKeyX(i), scale, offX);
            g.fillText(String.valueOf(n.getKey(i)), cx, cy);
        }

        // Дочерние узлы
        if (!n.isLeaf()) {
            for (int i = 0; i <= n.getNumKeys(); i++) {
                drawNodes(g, n.getChild(i), scale, offX, offY);
            }
        }
    }
}