package model;

import java.util.ArrayList;
import java.util.List;

public class Product implements Editable, CatalogItem {

    protected long itemNumber;
    protected String productName;
    protected String category;
    protected long price;
    protected int remainder;

    public Product(long itemNumber, String productName, String category,
                   long price, int remainder) {
        this.itemNumber = itemNumber;
        this.productName = productName;
        this.category = category;
        this.price = price;
        this.remainder = remainder;
    }

    @Override
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        if (checkToInt(itemNumber))
            errors.add("itemNumber = " + itemNumber + " не вмещается в int ("
                    + Integer.MIN_VALUE + ".." + Integer.MAX_VALUE + ")");
        if (checkToInt(price))
            errors.add("price = " + price + " не вмещается в int ("
                    + Integer.MIN_VALUE + ".." + Integer.MAX_VALUE + ")");

        if (itemNumber <= 0)
            errors.add("itemNumber должен быть больше 0");
        if (productName == null || productName.isBlank())
            errors.add("productName не задано");
        if (category == null || category.isBlank())
            errors.add("category не задано");
        if (price < 0)
            errors.add("price не может быть отрицательным");
        if (remainder < 0 || remainder >= 100)
            errors.add("remainder не может быть отрицательным или большим 99");

        return errors;
    }

    //проверка на диапазон переданного числа
    private static boolean checkToInt(long value) {
        return value < Integer.MIN_VALUE || value > Integer.MAX_VALUE;
    }

    public void setValues(long itemNumber, String productName, String category,
                          long price, int remainder) {
        this.itemNumber = itemNumber;
        this.productName = productName;
        this.category = category;
        this.price = price;
        this.remainder = remainder;
    }

    public void copyFrom(Product other) {
        setValues(other.itemNumber, other.productName,
                other.category, other.price, other.remainder);
    }

    public long getItemNumberRaw() { return itemNumber; }
    public long getPriceRaw() { return price; }

    @Override public int getItemNumber()     { return Math.toIntExact(itemNumber); }
    @Override public String getProductName() { return productName; }
    @Override public String getCategory()    { return category; }
    @Override public int getPrice()          { return Math.toIntExact(price); }
    @Override public int getRemainder()      { return remainder; }
    @Override public EntityType getType() { return EntityType.PRODUCT; }

    @Override
    public String toString() {
        return "Product{itemNumber=" + itemNumber + ", productName='" + productName
                + "', category='" + category + "', price=" + price
                + ", remainder=" + remainder + '}';
    }
}