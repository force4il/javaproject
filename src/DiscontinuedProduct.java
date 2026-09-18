/** read-only class*/
public record ReadOnlyProduct(int itemNumber, String productName, String category,
                              int price, int remainder) {

    public ReadOnlyProduct(Product p) {
        this(p.getItemNumber(), p.getProductName(), p.getCategory(), p.getPrice(), p.getRemainder());
    }

}