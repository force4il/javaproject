package model;

/** read-only класс. */
public record DiscontinuedProduct(long itemNumber, String productName, String category,
                                  long price, int remainder) implements CatalogItem {

    public DiscontinuedProduct(Product p) {
        this(p.getItemNumberRaw(), p.getProductName(), p.getCategory(),
                p.getPriceRaw(), p.getRemainder());
    }

    @Override public int getItemNumber() { return Math.toIntExact(itemNumber); }
    @Override public String getProductName() { return productName; }
    @Override public String getCategory() { return category; }
    @Override public int getPrice() { return Math.toIntExact(price); }
    @Override public int getRemainder() { return remainder; }
    @Override public EntityType getType() { return EntityType.DISCONTINUED; }
}