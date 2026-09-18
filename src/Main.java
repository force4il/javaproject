import java.time.LocalDate;

public class Main {
    public static void main(String[] args) {
//        Gui.main(null);
        DiscountedProduct product = new DiscountedProduct(1, "t", "t", 1000, 10, 10, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 20));
        System.out.println(product.getDiscountedPrice());
        Product p = new Product(1, "Phone", "Electronics", 1000, 5);
        ReadOnlyProduct ro = new ReadOnlyProduct(p);

    }
}
