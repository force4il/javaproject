package gui;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;

/** Все диалоги и парсеры ввода */
public final class InputDialogs {

    private static final String CSV_EXT = "*.csv";
    private static final int DEFAULT_GEN = 10000;
    public static final int MAX_GEN_COUNT = 70000;


    private InputDialogs() {}

    public static Integer askGenCount(Stage owner) {
        TextField field = new TextField(String.valueOf(DEFAULT_GEN));
        Dialog<Integer> dlg = new Dialog<>();
        dlg.initOwner(owner);
        dlg.setTitle("Генерация");
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane gp = new GridPane();
        gp.setHgap(8); gp.setVgap(8); gp.setPadding(new Insets(12));
        gp.add(new Label("Количество:"), 0, 0);
        gp.add(field, 1, 0);
        dlg.getDialogPane().setContent(gp);

        Button ok = (Button) dlg.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(ActionEvent.ACTION, evt -> {
            try {
                int v = Integer.parseInt(field.getText().trim());
                if (v <= 0)
                    throw new NumberFormatException();

                if (v > MAX_GEN_COUNT) {
                    Dialogs.showError(owner, "Генерация",
                            "Максимум " + MAX_GEN_COUNT + " записей");

                    evt.consume();
                }
            } catch (NumberFormatException e) {
                Dialogs.showError(owner, "Генерация", "Введите положительное число");
                evt.consume();
            }
        });
        dlg.setResultConverter(bt -> bt == ButtonType.OK
                ? Integer.parseInt(field.getText().trim()) : 0);
        return dlg.showAndWait().orElse(0);
    }

    public static File chooseOpenCsv(Stage owner, Path currentFile) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Открыть CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", CSV_EXT));
        applyInitialDir(chooser, currentFile);
        return chooser.showOpenDialog(owner);
    }

    public static File chooseSaveCsv(Stage owner, Path currentFile) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Сохранить CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", CSV_EXT));
        chooser.setInitialFileName(currentFile != null
                ? currentFile.getFileName().toString() : "data.csv");
        applyInitialDir(chooser, currentFile);
        return chooser.showSaveDialog(owner);
    }

    private static void applyInitialDir(FileChooser chooser, Path currentFile) {
        if (currentFile == null || currentFile.getParent() == null)
            return;

        File dir = currentFile.getParent().toFile();
        if (dir.isDirectory())
            chooser.setInitialDirectory(dir);
    }

    public static Integer parseOrNull(String s) {
        if (s == null || s.isBlank())
            return null;

        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public static boolean isNumericWithOptionalSign(String s) {
        if (s == null || s.isEmpty())
            return false;

        int start = (s.charAt(0) == '-' || s.charAt(0) == '+') ? 1 : 0;
        if (start == s.length())
            return false;

        for (int i = start; i < s.length(); i++)
            if (!Character.isDigit(s.charAt(i)))
                return false;

        return true;
    }
}