package gui;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Window;

import java.time.LocalDate;
import java.util.List;

import model.Product;
import model.ProductWithWarranty;

public final class ProductDialog extends Dialog<Product> {

    private static final String TYPE_PRODUCT  = "Product";
    private static final String TYPE_WARRANTY = "Warranty";

    private Product lastResult;

    public ProductDialog(Window owner, Product existing) {
        initOwner(owner);
        setTitle(existing == null ? "Добавить товар" : "Редактировать товар");
        setResizable(true);

        boolean editing = existing != null;
        boolean warranty = existing instanceof ProductWithWarranty;

        ToggleGroup typeGroup = new ToggleGroup();
        RadioButton productRadio  = new RadioButton(TYPE_PRODUCT);
        RadioButton warrantyRadio = new RadioButton(TYPE_WARRANTY);
        productRadio.setToggleGroup(typeGroup);
        warrantyRadio.setToggleGroup(typeGroup);
        (warranty ? warrantyRadio : productRadio).setSelected(true);
        productRadio.setDisable(editing);
        warrantyRadio.setDisable(editing);

        TextField itemNumberField = new TextField(existing == null ? "" : String.valueOf(existing.getItemNumber()));
        TextField nameField       = new TextField(existing == null ? "" : existing.getProductName());
        TextField categoryField   = new TextField(existing == null ? "" : existing.getCategory());
        TextField priceField      = new TextField(existing == null ? "" : String.valueOf(existing.getPrice()));
        TextField remainderField  = new TextField(existing == null ? "" : String.valueOf(existing.getRemainder()));

        DatePicker startDatePicker = new DatePicker();
        TextField  warrantyMonths  = new TextField();
        if (existing instanceof ProductWithWarranty pw) {
            startDatePicker.setValue(pw.getStartOfWarranty());
            warrantyMonths.setText(String.valueOf(pw.getWarrantyMonths()));
        }
        startDatePicker.disableProperty().bind(warrantyRadio.selectedProperty().not());
        warrantyMonths.disableProperty().bind(warrantyRadio.selectedProperty().not());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(15));

        int row = 0;
        grid.add(new Label("Тип:"), 0, row);
        grid.add(new HBox(10, productRadio, warrantyRadio), 1, row++);
        grid.add(new Label("Артикул:"), 0, row);
        grid.add(itemNumberField, 1, row++);
        grid.add(new Label("Название:"), 0, row);
        grid.add(nameField, 1, row++);
        grid.add(new Label("Категория:"), 0, row);
        grid.add(categoryField, 1, row++);
        grid.add(new Label("Цена:"), 0, row);
        grid.add(priceField, 1, row++);
        grid.add(new Label("Остаток:"), 0, row);
        grid.add(remainderField, 1, row++);
        grid.add(new Label("Начало гарантии:"), 0, row);
        grid.add(startDatePicker, 1, row++);
        grid.add(new Label("Гарантия (мес):"), 0, row);
        grid.add(warrantyMonths, 1, row);

        getDialogPane().setContent(grid);

        ButtonType okType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        Button okButton = (Button) getDialogPane().lookupButton(okType);
        okButton.addEventFilter(ActionEvent.ACTION, evt -> {
            try {
                Product candidate = parseResult(productRadio, itemNumberField, nameField,
                        categoryField, priceField, remainderField,
                        startDatePicker, warrantyMonths);

                // здесь же отдаём работу бизнес-валидации из Editable
                List<String> errors = candidate.validate();
                if (!errors.isEmpty()) {
                    throw new IllegalArgumentException(String.join("\n", errors));
                }
                lastResult = candidate;
            } catch (IllegalArgumentException ex) {
                Dialogs.showError(getOwner(), "Ошибка ввода", ex.getMessage());
                evt.consume();
            }
        });

        setResultConverter(bt -> bt == okType ? lastResult : null);
    }

    private static Product parseResult(RadioButton productRadio,
                                       TextField itemNumberField,
                                       TextField nameField,
                                       TextField categoryField,
                                       TextField priceField,
                                       TextField remainderField,
                                       DatePicker startDatePicker,
                                       TextField warrantyMonths) {
        int itemNumber = parseInt(itemNumberField, "Артикул");
        String name     = nameField.getText();
        String category = categoryField.getText();
        int price       = parseInt(priceField, "Цена");
        int remainder   = parseInt(remainderField, "Остаток");

        if (productRadio.isSelected()) {
            return new Product(itemNumber, name, category, price, remainder);
        }
        LocalDate start = startDatePicker.getValue();
        if (start == null) {
            throw new IllegalArgumentException("Не указана дата начала гарантии");
        }
        int months = parseInt(warrantyMonths, "Гарантия (мес)");
        return new ProductWithWarranty(itemNumber, name, category, price, remainder, start, months);
    }

    private static int parseInt(TextField field, String label) {
        String text = field.getText() == null ? "" : field.getText().trim();
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Поле «" + label + "» должно быть целым числом");
        }
    }
}