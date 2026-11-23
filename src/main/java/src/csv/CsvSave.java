package src.csv;

import com.opencsv.CSVWriterBuilder;
import src.exceptions.*;
import src.model.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class CsvSave {
    private static final char DELIMITER = ';';

    public static void save(List<CatalogItem> items, Path file) throws CsvException {
        save(items, file, CsvSchema.CURRENT_VERSION);
    }

    public static void save(List<CatalogItem> items, Path file, int targetVersion) throws CsvException {
        if (targetVersion < CsvSchema.MIN_VERSION || targetVersion > CsvSchema.CURRENT_VERSION)
            throw new CsvException(ErrorCode.PARSE_ERROR, "Неподдерживаемая версия CSV: " + targetVersion);

        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(file, "file");

        Path target = file.toAbsolutePath();
        Path temp = null;
        try {
            temp = Files.createTempFile(target.getParent(), "catalog-csv-", ".tmp");
            try (var writer = Files.newBufferedWriter(temp);
                 var csvWriter = new CSVWriterBuilder(writer).withSeparator(DELIMITER).build()) {
                 csvWriter.writeNext(new String[]{"#schema_version=" + targetVersion});
                 for (CatalogItem item : items)
                    csvWriter.writeNext(toLine(item, targetVersion));
            }

            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new CsvIOException(ErrorCode.FILE_WRITE, "Не удалось сохранить файл: " + file, e);
        } finally {
            if (temp != null)
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {

                }
        }
    }

    private static String[] toLine(CatalogItem item, int version) throws CsvException {
        return switch (item) {
            case ProductWithWarranty pw -> warrantyLine(pw, version);
            case Product p -> productLine(p, version);
            case DiscontinuedProduct dp -> new String[]{
                    EntityType.DISCONTINUED.getCsvName(),
                    String.valueOf(dp.getItemNumber()),
                    dp.getProductName(), dp.getCategory(),
                    String.valueOf(dp.getPrice()), String.valueOf(dp.getRemainder())
            };
            default -> throw new CsvException(ErrorCode.PARSE_ERROR,
                    "Неподдерживаемый тип: " + item.getClass().getName());
        };
    }

    private static String[] productLine(Product p, int version) {
        List<String> out = new ArrayList<>(10);
        out.add(EntityType.PRODUCT.getCsvName());
        out.add(String.valueOf(p.getItemNumber()));
        out.add(p.getProductName());
        out.add(p.getCategory());
        out.add(String.valueOf(p.getPrice()));
        out.add(String.valueOf(p.getRemainder()));
        if (version >= 2) {
            out.add(p.getSupplier() == null ? "" : p.getSupplier());
            out.add(p.getCountryOfOrigin() == null ? "" : p.getCountryOfOrigin());
        }
        if (version >= 3) {
            out.add(String.valueOf(p.getDiscountPercent()));
            out.add(p.getManufactureDate() == null ? "" : p.getManufactureDate().toString());
        }

        return out.toArray(new String[0]);
    }

    private static String[] warrantyLine(ProductWithWarranty pw, int version) {
        List<String> out = new ArrayList<>(12);
        out.add(EntityType.WARRANTY.getCsvName());
        out.add(String.valueOf(pw.getItemNumber()));
        out.add(pw.getProductName());
        out.add(pw.getCategory());
        out.add(String.valueOf(pw.getPrice()));
        out.add(String.valueOf(pw.getRemainder()));
        out.add(pw.getStartOfWarranty() != null ? pw.getStartOfWarranty().toString() : "");
        out.add(String.valueOf(pw.getWarrantyMonths()));
        if (version >= 2) {
            out.add(pw.getSupplier() == null ? "" : pw.getSupplier());
            out.add(pw.getCountryOfOrigin() == null ? "" : pw.getCountryOfOrigin());
        }
        if (version >= 3) {
            out.add(String.valueOf(pw.getDiscountPercent()));
            out.add(pw.getManufactureDate() == null ? "" : pw.getManufactureDate().toString());
        }

        return out.toArray(new String[0]);
    }
}