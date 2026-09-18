import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;

public class DiscountedProduct extends Product implements Editable {

    private int discount;
    private LocalDate startDiscount, endDiscount;

    @Override
    public List<String> validate() {
        List<String> errors = new ArrayList<>(super.validate());

        if (discount < 0 || discount > 100)
            errors.add("discount должен быть в диапазоне 0-100");
        if (startDiscount == null)
            errors.add("startDiscount не задана");
        if (endDiscount == null)
            errors.add("endDiscount не задана");
        if (startDiscount != null && endDiscount != null
                && startDiscount.isAfter(endDiscount))
            errors.add("startDiscount не может быть позже endDiscount");

        return errors;
    }

    public DiscountedProduct(int itemNumber, String productName, String category, int price,
                             int remainder, int discount, LocalDate startDiscount, LocalDate endDiscount) {
        super(itemNumber, productName, category, price, remainder);
        this.discount = discount;
        this.startDiscount = startDiscount;
        this.endDiscount = endDiscount;
    }

    public void setDiscount(int discount) {
        this.discount = discount;
    }

    public void setDiscountPeriod(LocalDate startDiscount, LocalDate endDiscount) {
        this.startDiscount = startDiscount;
        this.endDiscount = endDiscount;
    }

    public int getDiscount() {
        return discount;
    }

    public LocalDate getStartDiscount() {
        return startDiscount;
    }

    public LocalDate getEndDiscount() {
        return endDiscount;
    }

    //активна ли скидка
    public boolean isDiscountActive() {
        LocalDate today = LocalDate.now();
        boolean isActive = !today.isBefore(startDiscount) && !today.isAfter(endDiscount);
        return isActive && getRemainder() > 0;
    }

    public int getDiscountedPrice() {
        if (!isDiscountActive()) {
            return getPrice();
        }
        return getPrice() - getPrice() * discount / 100;
    }

}

