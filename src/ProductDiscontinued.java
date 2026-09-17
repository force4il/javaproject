public final class ProductDiscontinued {

    private final int itemNumber;
    private final String productName;
    private final String category;
    private final int price;
    private final int remainder;
    private final String discontinuedReason;

    public ProductDiscontinued(int itemNumber, String productName, String category,
                   int price, int remainder, String discontinuedReason) {
        this.itemNumber = itemNumber;
        this.productName = productName;
        this.category = category;
        this.price = price;
        this.remainder = remainder;
        this.discontinuedReason = discontinuedReason;
    }

    public int getItemNumber() {return itemNumber;}

    public String getProductName() {return productName;}

    public String getCategory() {return category;}

    public int getPrice() {return price;}

    public int getRemainder() {return remainder;}

    public String getDiscontinuedReason() {return discontinuedReason;}

    @Override
    public String toString() {
        return "ProductDiscontinued{" +
                "itemNumber=" + itemNumber +
                ", productName='" + productName + '\'' +
                ", category='" + category + '\'' +
                ", price=" + price +
                ", remainder=" + remainder +
                ", discontinuedReason='" + discontinuedReason + '\'' +
                '}';
    }

}