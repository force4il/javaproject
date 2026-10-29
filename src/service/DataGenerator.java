package service;

import model.CatalogItem;
import model.DiscontinuedProduct;
import model.Product;
import model.ProductWithWarranty;

import java.time.LocalDate;
import java.util.Random;

/** Генератор тестовых CatalogItem */
public final class DataGenerator {

    public static final int CATEGORIES = 5;
    public static final int PRICE_MIN = 100;
    public static final int PRICE_RANGE = 900;
    public static final int REMAINDER_EXCL = 100;
    public static final int WARRANTY_MIN_MONTHS = 6;
    public static final int WARRANTY_MONTHS_RANGE = 24;
    public static final int WARRANTY_DAYS_BACK = 365;
    public static final int KIND_COUNT = 3;

    private DataGenerator() {}

    public static CatalogItem randomItem(int key, Random rnd) {
        String name = "Item" + key;
        String cat = "Cat" + (rnd.nextInt(CATEGORIES) + 1);
        int price = PRICE_MIN + rnd.nextInt(PRICE_RANGE);
        int rem = rnd.nextInt(REMAINDER_EXCL);
        return switch (rnd.nextInt(KIND_COUNT)) {
            case 0 -> new Product(key, name, cat, price, rem);
            case 1 -> new ProductWithWarranty(key, name, cat, price, rem,
                    LocalDate.now().minusDays(rnd.nextInt(WARRANTY_DAYS_BACK)),
                    WARRANTY_MIN_MONTHS + rnd.nextInt(WARRANTY_MONTHS_RANGE));
            default -> new DiscontinuedProduct(key, name, cat, price, rem);
        };
    }
}