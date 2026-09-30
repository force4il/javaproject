package model;

/** read-only class */
public record DiscontinuedProduct(int itemNumber, String productName, String category,
                                  int price, int remainder) implements CatalogItem {

    public DiscontinuedProduct(Product p) {
        this(p.getItemNumber(), p.getProductName(), p.getCategory(),
                p.getPrice(), p.getRemainder());
    }

    @Override public int getItemNumber() { return itemNumber; }
    @Override public String getProductName() { return productName; }
    @Override public String getCategory() { return category; }
    @Override public int getPrice() { return price; }
    @Override public int getRemainder() { return remainder; }

    @Override public EntityType getType() { return EntityType.DISCONTINUED; }
}