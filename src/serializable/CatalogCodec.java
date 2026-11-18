package serializable;

import model.*;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

final class CatalogCodec {
    static final int MAX_ITEMS = 1_000_000;
    static final long MAX_FILE_BYTES = 256L * 1024 * 1024;

    private CatalogCodec() {}

    static void checkCancelled() {
        if (Thread.currentThread().isInterrupted())
            throw new java.util.concurrent.CancellationException("Операция отменена");
    }

    static CatalogItem create(String type, long id, String name, String category,
                              long price, int stock, String start, Integer months,
                              String supplier, String countryOfOrigin,
                              int discountPercent, LocalDate manufactureDate)
            throws IOException {

        if (id < 1 || id > Integer.MAX_VALUE)
            throw new IOException("Некорректный артикул: " + id);
        if (price < 0 || price > Integer.MAX_VALUE)
            throw new IOException("Некорректная цена: " + price + " (артикул " + id + ")");
        if (stock < 0 || stock >= Product.MAX_REMAINDER)
            throw new IOException("Некорректный остаток: " + stock + " (артикул " + id + ")");
        if (name == null || name.isBlank())
            throw new IOException("Пустое имя у товара " + id);
        if (category == null || category.isBlank())
            throw new IOException("Пустая категория у товара " + id);

        String sup = supplier == null ? "" : supplier;
        String cnt = countryOfOrigin == null ? "" : countryOfOrigin;

        CatalogItem item;
        try {
            item = switch (type) {
                case "Product" -> new Product(id, name, category, price, stock,
                        sup, cnt, discountPercent, manufactureDate);
                case "Warranty" -> {
                    if (start == null || months == null)
                        throw new IOException("Нет данных гарантии для товара " + id);
                    yield new ProductWithWarranty(id, name, category, price, stock,
                            LocalDate.parse(start), months,
                            sup, cnt, discountPercent, manufactureDate);
                }
                case "Discontinued" -> {
                    if (!sup.isEmpty() || !cnt.isEmpty() || discountPercent != 0
                            || manufactureDate != null)
                        throw new IOException("Поля v2/v3 не применимы к Discontinued (артикул " + id + ")");
                    yield new DiscontinuedProduct(id, name, category, price, stock);
                }
                default -> throw new IOException("Неизвестный тип: " + type);
            };
        } catch (DateTimeParseException e) {
            throw new IOException("Некорректная дата начала гарантии: '" + start
                    + "' (артикул " + id + ")", e);
        } catch (RuntimeException e) {
            throw new IOException("Ошибка восстановления товара " + id, e);
        }

        if (item instanceof Editable e) {
            List<String> errors = e.validate();
            if (!errors.isEmpty())
                throw new IOException("Некорректная запись (артикул " + id + "): "
                        + String.join("; ", errors));
        }
        return item;
    }

    static List<CatalogItem> checked(List<CatalogItem> items) throws IOException {
        if (items == null || items.size() > MAX_ITEMS)
            throw new IOException("Недопустимое количество товаров");

        Set<Integer> keys = new HashSet<>();
        for (CatalogItem item : items) {
            checkCancelled();
            if (item == null)
                throw new IOException("Пустой товар");

            String start = null;
            Integer months = null;
            String supplier = "";
            String country = "";
            int discount = 0;
            LocalDate mfg = null;

            if (item instanceof ProductWithWarranty w) {
                start = String.valueOf(w.getStartOfWarranty());
                months = w.getWarrantyMonths();
            }
            if (item instanceof Product p) {
                supplier = p.getSupplier();
                country = p.getCountryOfOrigin();
                discount = p.getDiscountPercent();
                mfg = p.getManufactureDate();
            }

            create(item.getType().getCsvName(), item.getItemNumber(),
                    item.getProductName(), item.getCategory(),
                    item.getPrice(), item.getRemainder(),
                    start, months, supplier, country, discount, mfg);

            if (!keys.add(item.getItemNumber()))
                throw new IOException("Повтор артикула: " + item.getItemNumber());
        }
        return List.copyOf(items);
    }

    static CatalogItem fromRecord(CatalogRecord r) throws IOException {
        if (r == null || r.type == null)
            throw new IOException("Пустая запись каталога");

        boolean discontinued = r.isDiscontinued();

        LocalDate mfg = null;
        if (!discontinued && r.manufactureDate != null) {
            String mfgStr = r.manufactureDate.trim();
            if (!mfgStr.isEmpty()) {
                try {
                    mfg = LocalDate.parse(mfgStr);
                } catch (DateTimeParseException e) {
                    throw new MigrationException(MigrationException.Reason.BAD_FIELD,
                            "Некорректная дата manufactureDate: '" + r.manufactureDate + "'", e);
                }
            }
        }

        int discount = r.discountPercent == null ? 0 : r.discountPercent;
        String sup = r.supplier == null ? "" : r.supplier;
        String cnt = r.countryOfOrigin == null ? "" : r.countryOfOrigin;

        return create(r.type, r.itemNumber, r.productName, r.category, r.price, r.remainder,
                r.startOfWarranty, r.warrantyMonths, sup, cnt, discount, mfg);
    }
}