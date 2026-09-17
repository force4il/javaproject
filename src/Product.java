public class Product {

    private int itemNumber;
    private String productName;
    private String category;
    private int price;
    private int remainder;


    public Product(int itemNumber, String productName, String category,
                   int price, int remainder) {
        this.itemNumber = itemNumber;
        this.productName = productName;
        this.category = category;
        this.price = price;
        this.remainder = remainder;
    }

    public void setValues(int itemNumber, String productName, String category,
                          int price, int remainder) {
        this.itemNumber = itemNumber;
        this.productName = productName;
        this.category = category;
        this.price = price;
        this.remainder = remainder;
    }

    public int getItemNumber() {return itemNumber;}

    public String getProductName() {return productName;}

    public String getCategory() {return category;}

    public int getPrice() {return price;}

    public int getRemainder() {return remainder;}

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