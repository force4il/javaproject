package serializable;

import model.*;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

final class CatalogCodec {
    static final int MAX_ITEMS = 1000000;
    static final long MAX_FILE_BYTES = 256L * 1024 * 1024;

    private CatalogCodec() {}

    static void checkCancelled() {
        if (Thread.currentThread().isInterrupted())
            throw new java.util.concurrent.CancellationException("Операция отменена");
    }

    static CatalogItem create(String type, long id, String name, String category,
                              long price, int stock, String start, Integer months) throws IOException {
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

        CatalogItem item;
        try {
            item = switch (type) {
                case "Product" -> new Product(id, name, category, price, stock);
                case "Warranty" -> {
                    if (start == null || months == null)
                        throw new IOException("Нет данных гарантии для товара " + id);
                    yield new ProductWithWarranty(id, name, category, price, stock,
                            LocalDate.parse(start), months);
                }
                case "Discontinued" -> new DiscontinuedProduct(id, name, category, price, stock);
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
                throw new IOException("Некорректная запись: " + String.join("; ", errors));
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

            String start = item instanceof ProductWithWarranty w
                    ? String.valueOf(w.getStartOfWarranty()) : null;
            Integer months = item instanceof ProductWithWarranty w
                    ? w.getWarrantyMonths() : null;

            create(item.getType().getCsvName(), item.getItemNumber(), item.getProductName(),
                    item.getCategory(), item.getPrice(), item.getRemainder(), start, months);

            if (!keys.add(item.getItemNumber()))
                throw new IOException("Повтор артикула: " + item.getItemNumber());
        }
        return List.copyOf(items);
    }
}