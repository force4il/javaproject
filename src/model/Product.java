package model;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.time.LocalDate;
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

    // v2
    protected String supplier;
    protected String countryOfOrigin;
    // v3
    protected int discountPercent;
    protected LocalDate manufactureDate;

    public static final int MIN_ITEM_NUMBER = 1;
    public static final int MIN_NUM_BORDER = 0;
    public static final int MAX_REMAINDER = 100;
    public static final int MIN_DISCOUNT_PERCENT = 0;
    public static final int MAX_DISCOUNT_PERCENT = 100;

    public static final LocalDate DEFAULT_MANUFACTURE_DATE = LocalDate.of(1970, 1, 1);

    public Product(long itemNumber, String productName, String category, long price, int remainder) {
        this(itemNumber, productName, category, price, remainder, "", "", 0, DEFAULT_MANUFACTURE_DATE);
    }

    public Product(long itemNumber, String productName, String category,
                   long price, int remainder,
                   String supplier, String countryOfOrigin,
                   int discountPercent, LocalDate manufactureDate) {
        this.itemNumber = itemNumber;
        this.productName = productName;
        this.category = category;
        this.price = price;
        this.remainder = remainder;
        this.supplier = supplier == null ? "" : supplier;
        this.countryOfOrigin = countryOfOrigin == null ? "" : countryOfOrigin;
        this.discountPercent = discountPercent;
        this.manufactureDate = manufactureDate == null ? DEFAULT_MANUFACTURE_DATE : manufactureDate;
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (supplier == null) supplier = "";
        if (countryOfOrigin == null) countryOfOrigin = "";
        if (manufactureDate == null) manufactureDate = DEFAULT_MANUFACTURE_DATE;
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
            errors.add("remainder не может быть меньше " + MIN_NUM_BORDER
                    + " или большим " + MAX_REMAINDER);
        if (discountPercent < MIN_DISCOUNT_PERCENT || discountPercent > MAX_DISCOUNT_PERCENT)
            errors.add("discountPercent вне диапазона ["
                    + MIN_DISCOUNT_PERCENT + ".." + MAX_DISCOUNT_PERCENT + "]");
        if (manufactureDate != null && manufactureDate.isAfter(LocalDate.now()))
            errors.add("manufactureDate не может быть в будущем");
        if (manufactureDate != null && manufactureDate.isAfter(LocalDate.now()))
            errors.add("manufactureDate не может быть в будущем");

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

    public String getSupplier() { return supplier; }
    public String getCountryOfOrigin() { return countryOfOrigin; }
    public int getDiscountPercent() { return discountPercent; }
    public LocalDate getManufactureDate() { return manufactureDate; }

    @Override
    public String toString() {
        return "Product{itemNumber=" + itemNumber + ", productName='" + productName
                + "', category='" + category + "', price=" + price
                + ", remainder=" + remainder + ", supplier='" + supplier
                + "', country='" + countryOfOrigin + "', discount=" + discountPercent
                + ", mfg=" + manufactureDate + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product other = (Product) o;
        return itemNumber == other.itemNumber
                && price == other.price
                && remainder == other.remainder
                && discountPercent == other.discountPercent
                && Objects.equals(productName, other.productName)
                && Objects.equals(category, other.category)
                && Objects.equals(supplier, other.supplier)
                && Objects.equals(countryOfOrigin, other.countryOfOrigin)
                && Objects.equals(manufactureDate, other.manufactureDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemNumber, productName, category, price, remainder,
                supplier, countryOfOrigin, discountPercent, manufactureDate);
    }
}