package gui;

import exceptions.AppException;
import exceptions.CsvException;
import exceptions.TreeException;
import javafx.scene.control.Alert;
import javafx.stage.Window;

public final class Dialogs {

    public static void showError(Window owner, String title, String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.initOwner(owner);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
    }

    public static void showInfo(Window owner, String title, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.initOwner(owner);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
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
}