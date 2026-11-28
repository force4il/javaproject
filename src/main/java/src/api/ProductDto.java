package src.api;

import src.exceptions.api.ReadOnlyException;
import src.exceptions.api.ValidationException;
import src.model.*;

import java.time.LocalDate;

public record ProductDto(
        String type,
        Long itemNumber,
        String productName,
        String category,
        Long price,
        Integer remainder,
        String supplier,
        String countryOfOrigin,
        Integer discountPercent,
        LocalDate manufactureDate,
        LocalDate startOfWarranty,
        Integer warrantyMonths
) {

    public CatalogItem toEntity() {
        if (itemNumber == null || price == null || remainder == null)
            throw new ValidationException("Обязательные числовые поля отсутствуют");

        String kind = type == null ? "Product" : type;
        int discount = discountPercent == null ? 0 : discountPercent;

        return switch (kind) {
            case "Product" -> new Product(
                    itemNumber,
                    productName,
                    category,
                    price,
                    remainder,
                    supplier,
                    countryOfOrigin,
                    discount,
                    manufactureDate
            );

            case "Warranty" -> new ProductWithWarranty(
                    itemNumber,
                    productName,
                    category,
                    price,
                    remainder,
                    startOfWarranty,
                    warrantyMonths == null ? 0 : warrantyMonths,
                    supplier,
                    countryOfOrigin,
                    discount,
                    manufactureDate
            );
            case "Discontinued" -> throw new ReadOnlyException("Нельзя создавать read-only товар через API");
            default -> throw new ValidationException("Неизвестный тип товара");
        };
    }

    public static ProductDto toDomainEntity(CatalogItem i) {
        Product p = i instanceof Product x ? x : null;
        ProductWithWarranty w = i instanceof ProductWithWarranty x ? x : null;
        return new ProductDto(
                i.getType().getCsvName(),
                (long) i.getItemNumber(),
                i.getProductName(),
                i.getCategory(),
                (long) i.getPrice(),
                i.getRemainder(),
                p == null ? null : p.getSupplier(),
                p == null ? null : p.getCountryOfOrigin(),
                p == null ? null : p.getDiscountPercent(),
                p == null ? null : p.getManufactureDate(),
                w == null ? null : w.getStartOfWarranty(),
                w == null ? null : w.getWarrantyMonths()
        );
    }
}