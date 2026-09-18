import java.util.List;
import java.util.ArrayList;

public class Product implements Editable {

    protected int itemNumber;
    protected String productName;
    protected String category;
    protected int price;
    protected int remainder;

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

    public Product(int itemNumber, String productName, String category,
                   int price, int remainder) {
        this.itemNumber = itemNumber;
        this.productName = productName;
        this.category = category;
        this.price = price;
        this.remainder = remainder;
    }

    //сеттер
    public void setValues(int itemNumber, String productName, String category,
                          int price, int remainder) {
        this.itemNumber = itemNumber;
        this.productName = productName;
        this.category = category;
        this.price = price;
        this.remainder = remainder;
    }

    //геттеры
    public int getItemNumber() {
        return itemNumber;
    }

    public String getProductName() {
        return productName;
    }

    public String getCategory() {
        return category;
    }

    public int getPrice() {
        return price;
    }

    public int getRemainder() {
        return remainder;
    }

    @Override
    public String toString() {
        return "Product{" +
                "itemNumber=" + itemNumber +
                ", productName='" + productName + '\'' +
                ", category='" + category + '\'' +
                ", price=" + price +
                ", remainder=" + remainder +
                '}';
    }

}