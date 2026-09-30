package model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ProductWithWarranty extends Product {

    private LocalDate startOfWarranty;
    private int warrantyMonths;

    public ProductWithWarranty(int itemNumber, String productName, String category, int price,
                               int remainder, LocalDate startOfWarranty, int warrantyMonths) {
        super(itemNumber, productName, category, price, remainder);
        this.startOfWarranty = startOfWarranty;
        this.warrantyMonths = warrantyMonths;
    }

    @Override
    public List<String> validate() {
        List<String> errors = new ArrayList<>(super.validate());
        if (startOfWarranty == null)
            errors.add("startOfWarranty не задана");
        if (warrantyMonths <= 0)
            errors.add("warrantyMonths должен быть больше 0");
        if (startOfWarranty != null && startOfWarranty.isAfter(LocalDate.now()))
            errors.add("дата начала гарантии не может быть больше сегодняшней даты");
        return errors;
    }

    @Override
    public void copyFrom(Product other) {
        super.copyFrom(other);
        if (other instanceof ProductWithWarranty pw) {
            this.startOfWarranty = pw.getStartOfWarranty();
            this.warrantyMonths = pw.getWarrantyMonths();
        }
    }

    @Override
    public EntityType getType() { return EntityType.WARRANTY; }

    public void setStartOfWarranty(LocalDate startOfWarranty) {
        this.startOfWarranty = startOfWarranty;
    }
    public void setWarrantyMonths(int warrantyMonths) {
        this.warrantyMonths = warrantyMonths;
    }

    public LocalDate getStartOfWarranty() {
        return startOfWarranty;
    }
    public int getWarrantyMonths() {
        return warrantyMonths;
    }

    public LocalDate getWarrantyEndDate() {
        return startOfWarranty.plusMonths(warrantyMonths);
    }
    public boolean isUnderWarranty() {
        return !LocalDate.now().isAfter(getWarrantyEndDate());
    }

    @Override
    public String toString() {
        return "model.ProductWithWarranty{" +
                "itemNumber=" + itemNumber +
                ", productName='" + productName + '\'' +
                ", category='" + category + '\'' +
                ", price=" + price +
                ", remainder=" + remainder +
                ", startOfWarranty=" + startOfWarranty +
                ", warrantyMonths=" + warrantyMonths +
                ", isUnderWarranty=" + isUnderWarranty() +
                '}';
    }
}