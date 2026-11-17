package model;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Product implements Editable, CatalogItem {

    @Serial
    private static final long serialVersionUID = 1L;

    protected long itemNumber;
    protected String productName;
    protected String category;
    protected long price;
    protected int remainder;

    public static final int MIN_ITEM_NUMBER = 1;
    public static final int MIN_NUM_BORDER = 0;
    public static final int MAX_REMAINDER = 100;

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

        if (itemNumber < MIN_ITEM_NUMBER)
            errors.add("itemNumber должен быть больше " + MIN_ITEM_NUMBER);
        if (productName == null || productName.isBlank())
            errors.add("productName не задано");
        if (category == null || category.isBlank())
            errors.add("category не задано");
        if (price < MIN_NUM_BORDER)
            errors.add("price не может быть меньше " + MIN_NUM_BORDER);
        if (remainder < MIN_NUM_BORDER || remainder >= MAX_REMAINDER)
            errors.add("remainder не может быть меньше " + MIN_NUM_BORDER + " или большим " + MAX_REMAINDER);

        return errors;
    }

    private static boolean checkToInt(long value) {
        return value < Integer.MIN_VALUE || value > Integer.MAX_VALUE;
    }

    public long getItemNumberRaw() { return itemNumber; }
    public long getPriceRaw() { return price; }

    @Override public int getItemNumber() { return Math.toIntExact(itemNumber); }
    @Override public String getProductName() { return productName; }
    @Override public String getCategory() { return category; }
    @Override public int getPrice() { return Math.toIntExact(price); }
    @Override public int getRemainder() { return remainder; }
    @Override public EntityType getType() { return EntityType.PRODUCT; }

    @Override
    public String toString() {
        return "Product{itemNumber=" + itemNumber + ", productName='" + productName
                + "', category='" + category + "', price=" + price
                + ", remainder=" + remainder + '}';
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;   // тип учитываем
        Product other = (Product) o;
        return itemNumber == other.itemNumber
                && price == other.price
                && remainder == other.remainder
                && Objects.equals(productName, other.productName)
                && Objects.equals(category, other.category);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemNumber, productName, category, price, remainder);
    }
}