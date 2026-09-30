package model;

import java.util.ArrayList;
import java.util.List;

public class Product implements Editable, CatalogItem {

    protected int itemNumber;
    protected String productName;
    protected String category;
    protected int price;
    protected int remainder;

    public Product(int itemNumber, String productName, String category,
                   int price, int remainder) {
        this.itemNumber = itemNumber;
        this.productName = productName;
        this.category = category;
        this.price = price;
        this.remainder = remainder;
    }

    @Override
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (itemNumber <= 0)
            errors.add("itemNumber должен быть больше 0");
        if (productName == null || productName.isBlank())
            errors.add("productName не задано или там пустая строка");
        if (category == null || category.isBlank())
            errors.add("category не задано или там пустая строка");
        if (price < 0)
            errors.add("price не может быть отрицательным");
        if (remainder < 0)
            errors.add("remainder не может быть отрицательным");
        return errors;
    }

    public void setValues(int itemNumber, String productName, String category,
                          int price, int remainder) {
        this.itemNumber = itemNumber;
        this.productName = productName;
        this.category = category;
        this.price = price;
        this.remainder = remainder;
    }

    /**Копирует поля из другого Product*/
    public void copyFrom(Product other) {
        setValues(other.getItemNumber(), other.getProductName(),
                other.getCategory(), other.getPrice(), other.getRemainder());
    }

    @Override public int getItemNumber() { return itemNumber; }
    @Override public String getProductName() { return productName; }
    @Override public String getCategory() { return category; }
    @Override public int getPrice() { return price; }
    @Override public int getRemainder() { return remainder; }

    @Override
    public EntityType getType() { return EntityType.PRODUCT; }

    @Override
    public String toString() {
        return "model.Product{" +
                "itemNumber=" + itemNumber +
                ", productName='" + productName + '\'' +
                ", category='" + category + '\'' +
                ", price=" + price +
                ", remainder=" + remainder +
                '}';
    }
}