package serializable;

import java.util.Objects;
import model.CatalogItem;
import model.Product;
import model.ProductWithWarranty;

public final class CatalogRecord {

    public String type;
    public long itemNumber;
    public String productName;
    public String category;
    public long price;
    public int remainder;

    // Warranty-only
    public String startOfWarranty;
    public Integer warrantyMonths;

    // v2
    public String supplier;
    public String countryOfOrigin;

    // v3
    public Integer discountPercent;
    public String manufactureDate;

    public boolean isDiscontinued() {
        return "Discontinued".equals(type);
    }

    /** Копирует данные из готового CatalogItem (для сохранения по версиям). */
    public static CatalogRecord from(CatalogItem item) {
        Objects.requireNonNull(item);
        CatalogRecord r = new CatalogRecord();
        r.type = item.getType().getCsvName();
        r.itemNumber = item.getItemNumber();
        r.productName = item.getProductName();
        r.category = item.getCategory();
        r.price = item.getPrice();
        r.remainder = item.getRemainder();
        if (item instanceof ProductWithWarranty w) {
            r.startOfWarranty = w.getStartOfWarranty() == null ? null : w.getStartOfWarranty().toString();
            r.warrantyMonths = w.getWarrantyMonths();
        }
        if (item instanceof Product p) {
            r.supplier = p.getSupplier();
            r.countryOfOrigin = p.getCountryOfOrigin();
            r.discountPercent = p.getDiscountPercent();
            r.manufactureDate = p.getManufactureDate() == null ? null : p.getManufactureDate().toString();
        }

        return r;
    }
}