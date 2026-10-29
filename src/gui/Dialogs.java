package gui;

import csv.CsvLoader;
import exceptions.AppException;
import exceptions.CsvException;
import exceptions.TreeException;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

import java.util.List;

public final class Dialogs {

    private static final int MAX_SKIPPED_SHOWN = 50;

    private Dialogs() {}

    public static void showError(Window owner, String title, String message) {
        show(owner, Alert.AlertType.ERROR, title, message);
    }

    public static void showInfo(Window owner, String title, String message) {
        show(owner, Alert.AlertType.INFORMATION, title, message);
    }

    public static boolean confirm(Window owner, String title, String message) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.initOwner(owner);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        return a.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    public static void showCsvError(Window owner, String action, CsvException e) {
        StringBuilder sb = new StringBuilder();
        sb.append("Код ошибки: ").append(e.getCode()).append("\n\n")
                .append(e.getMessage());
        Throwable cause = e.getCause();
        if (cause != null) {
            sb.append("\n\nПричина: ").append(cause.getClass().getSimpleName())
                    .append(": ").append(cause.getMessage());
        }
        showError(owner, action + " — ошибка CSV", sb.toString());
    }

    public static void showTreeError(Window owner, String action, TreeException e) {
        showError(owner, action + " — ошибка дерева",
                "Код ошибки: " + e.getCode() + "\n\n" + e.getMessage());
    }

    public static void showAppError(Window owner, String action, AppException e) {
        showError(owner, action + " — ошибка",
                "Код ошибки: " + e.getCode() + "\n\n" + e.getMessage());
    }

    public static void showSkipped(Window owner, List<CsvLoader.SkippedRow> skipped) {
        StringBuilder sb = new StringBuilder("Пропущено строк: ")
                .append(skipped.size()).append("\n\n");
        int shown = Math.min(MAX_SKIPPED_SHOWN, skipped.size());
        for (int i = 0; i < shown; i++) {
            CsvLoader.SkippedRow s = skipped.get(i);
            sb.append("Строка ").append(s.lineNumber()).append(": ")
                    .append(s.cause().getMessage()).append('\n');
        }
        if (skipped.size() > MAX_SKIPPED_SHOWN)
            sb.append("… и ещё ").append(skipped.size() - MAX_SKIPPED_SHOWN);
        showInfo(owner, "Пропущенные строки", sb.toString());
    }

    private static void show(Window owner, Alert.AlertType type,
                             String title, String message) {
        Alert a = new Alert(type);
        a.initOwner(owner);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
    }
}