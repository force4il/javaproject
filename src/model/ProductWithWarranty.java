package model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ProductWithWarranty extends Product {

    private LocalDate startOfWarranty;
    private int warrantyMonths;

    public ProductWithWarranty(long itemNumber, String productName, String category,
                               long price, int remainder,
                               LocalDate startOfWarranty, int warrantyMonths) {
        super(itemNumber, productName, category, price, remainder);
        this.startOfWarranty = startOfWarranty;
        this.warrantyMonths  = warrantyMonths;
    }

    @Override
    public List<String> validate() {
        List<String> errors = new ArrayList<>(super.validate());
        if (startOfWarranty == null)
            errors.add("startOfWarranty не задана");
        if (warrantyMonths <= 0)
            errors.add("warrantyMonths должен быть > 0");
        if (startOfWarranty != null && startOfWarranty.isAfter(LocalDate.now()))
            errors.add("дата начала гарантии не может быть в будущем");

        return errors;
    }

    @Override
    public void copyFrom(Product other) {
        super.copyFrom(other);
        if (other instanceof ProductWithWarranty pw) {
            this.startOfWarranty = pw.getStartOfWarranty();
            this.warrantyMonths  = pw.getWarrantyMonths();
        }
    }

    @Override public EntityType getType() { return EntityType.WARRANTY; }

    public void setStartOfWarranty(LocalDate d) { this.startOfWarranty = d; }
    public void setWarrantyMonths (int m) { this.warrantyMonths = m; }

    public LocalDate getStartOfWarranty() { return startOfWarranty; }
    public int getWarrantyMonths() { return warrantyMonths; }

    public LocalDate getWarrantyEndDate() { return startOfWarranty.plusMonths(warrantyMonths); }
    public boolean isUnderWarranty() { return !LocalDate.now().isAfter(getWarrantyEndDate()); }

    @Override
    public String toString() {
        return "ProductWithWarranty{itemNumber=" + itemNumber
                + ", productName='" + productName + "', start=" + startOfWarranty
                + ", months=" + warrantyMonths + '}';
    }
}