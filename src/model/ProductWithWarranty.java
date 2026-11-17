package model;

import java.io.Serial;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ProductWithWarranty extends Product {

    @Serial
    private static final long serialVersionUID = 1L;

    private final LocalDate startOfWarranty;
    private final int warrantyMonths;

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

    @Override public EntityType getType() { return EntityType.WARRANTY; }

    public LocalDate getStartOfWarranty() { return startOfWarranty; }
    public int getWarrantyMonths() { return warrantyMonths; }

    public LocalDate getWarrantyEndDate() {
        return startOfWarranty == null ? null : startOfWarranty.plusMonths(warrantyMonths);
    }

    @Override
    public String toString() {
        return "ProductWithWarranty{itemNumber=" + itemNumber
                + ", productName='" + productName + "', start=" + startOfWarranty
                + ", months=" + warrantyMonths + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o)) return false;       // уже проверил getClass() и базовые поля
        ProductWithWarranty other = (ProductWithWarranty) o;  // безопасно
        return warrantyMonths == other.warrantyMonths
                && Objects.equals(startOfWarranty, other.startOfWarranty);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + Objects.hash(startOfWarranty, warrantyMonths);
    }
}