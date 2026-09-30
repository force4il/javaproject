package gui;

import exceptions.CsvException;
import javafx.scene.control.Alert;
import javafx.stage.Window;

/**Точка показа диалогов*/
public final class Dialogs {

    public static void showError(Window owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void showInfo(Window owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**Показ исключений CSV с кодом ошибки*/
    public static void showCsvError(Window owner, String action, CsvException e) {
        showError(owner,
                action + " — ошибка",
                "Код ошибки: " + e.getCode() + "\n\n" + e.getMessage());
    }
}