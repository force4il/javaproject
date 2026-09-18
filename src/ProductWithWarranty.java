import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;

public class ProductWithWarranty extends Product implements Editable {

    private LocalDate startOfWarranty;
    private int warrantyMonths;

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

    public ProductWithWarranty(int itemNumber, String productName, String category, int price,
                               int remainder, LocalDate startOfWarranty, int warrantyMonths) {
        super(itemNumber, productName, category, price, remainder);
        this.startOfWarranty = startOfWarranty;
        this.warrantyMonths = warrantyMonths;
    }

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

    //дата окончания гарантии
    public LocalDate getWarrantyEndDate() {
        return startOfWarranty.plusMonths(warrantyMonths);
    }

    //действует ли еще гарантия
    public boolean isUnderWarranty() {
        return !LocalDate.now().isAfter(getWarrantyEndDate());
    }

    @Override
    public String toString() {
        return "ProductWithWarranty{" +
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
