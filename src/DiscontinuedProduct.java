/** read-only class*/
public record DiscontinuedProduct(int itemNumber, String productName, String category,
                              int price, int remainder) {

    public DiscontinuedProduct(Product p) {
        this(p.getItemNumber(), p.getProductName(), p.getCategory(), p.getPrice(), p.getRemainder());
    }

}