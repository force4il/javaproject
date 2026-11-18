package serializable;

import model.*;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CancellationException;

public final class BinaryCatalogSerializer implements Serializer<List<CatalogItem>> {

    private static final int HEADER_MAGIC = 0x42434154;

    // DTOs per version — serialized inside ObjectOutputStream.
    record ItemV1Dto(String type, long itemNumber, String productName, String category,
                     long price, int remainder, String startOfWarranty, Integer warrantyMonths)
            implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }

    record ItemV2Dto(String type, long itemNumber, String productName, String category,
                     long price, int remainder, String startOfWarranty, Integer warrantyMonths,
                     String supplier, String countryOfOrigin)
            implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }

    record ItemV3Dto(String type, long itemNumber, String productName, String category,
                     long price, int remainder, String startOfWarranty, Integer warrantyMonths,
                     String supplier, String countryOfOrigin,
                     int discountPercent, String manufactureDate)
            implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }

    @Override public String format() { return "bin"; }
    @Override public int currentVersion() { return CatalogVersions.CURRENT; }

    @Override
    public String describeDowngradeLoss(int targetVersion) {
        return CatalogVersions.lossFromTo(currentVersion(), targetVersion);
    }

    @Override
    public void save(List<CatalogItem> data, Path file, int targetVersion) throws IOException {
        CatalogVersions.validateSupported(targetVersion);
        List<CatalogItem> items = CatalogCodec.checked(data);

        Path target = file.toAbsolutePath();
        Path temp = Files.createTempFile(target.getParent(), ".catalog-", ".tmp");
        try {
            try (ObjectOutputStream out = new ObjectOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(temp)))) {
                out.writeInt(HEADER_MAGIC);
                out.writeInt(targetVersion);
                out.writeObject(toDtos(items, targetVersion));
            }
            checkCancelled();
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                throw new IOException("Файловая система не поддерживает атомарное сохранение", ex);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static List<Object> toDtos(List<CatalogItem> items, int version) {
        List<Object> out = new ArrayList<>(items.size());
        for (CatalogItem it : items) {
            CatalogCodec.checkCancelled();
            switch (version) {
                case 1 -> out.add(toV1(it));
                case 2 -> out.add(toV2(it));
                case 3 -> out.add(toV3(it));
                default -> throw new IllegalStateException("bad version " + version);
            }
        }
        return out;
    }

    private static ItemV1Dto toV1(CatalogItem it) {
        String start = null; Integer months = null;
        if (it instanceof ProductWithWarranty w) {
            start = w.getStartOfWarranty() == null ? null : w.getStartOfWarranty().toString();
            months = w.getWarrantyMonths();
        }
        return new ItemV1Dto(it.getType().getCsvName(), it.getItemNumber(),
                it.getProductName(), it.getCategory(), it.getPrice(), it.getRemainder(),
                start, months);
    }

    private static ItemV2Dto toV2(CatalogItem it) {
        String start = null, sup = "", cnt = ""; Integer months = null;
        if (it instanceof ProductWithWarranty w) {
            start = w.getStartOfWarranty().toString(); months = w.getWarrantyMonths();
        }
        if (it instanceof Product p) {
            sup = p.getSupplier() == null ? "" : p.getSupplier();
            cnt = p.getCountryOfOrigin() == null ? "" : p.getCountryOfOrigin();
        }
        return new ItemV2Dto(it.getType().getCsvName(), it.getItemNumber(),
                it.getProductName(), it.getCategory(), it.getPrice(), it.getRemainder(),
                start, months, sup, cnt);
    }

    private static ItemV3Dto toV3(CatalogItem it) {
        String start = null, sup = "", cnt = "", mfg = null; Integer months = null; int disc = 0;
        if (it instanceof ProductWithWarranty w) {
            start = w.getStartOfWarranty().toString(); months = w.getWarrantyMonths();
        }
        if (it instanceof Product p) {
            sup = p.getSupplier() == null ? "" : p.getSupplier();
            cnt = p.getCountryOfOrigin() == null ? "" : p.getCountryOfOrigin();
            disc = p.getDiscountPercent();
            mfg = p.getManufactureDate().toString();
        }
        return new ItemV3Dto(it.getType().getCsvName(), it.getItemNumber(),
                it.getProductName(), it.getCategory(), it.getPrice(), it.getRemainder(),
                start, months, sup, cnt, disc, mfg);
    }

    @Override
    public LoadResult<List<CatalogItem>> load(Path file) throws IOException {
        if (Files.size(file) > CatalogCodec.MAX_FILE_BYTES)
            throw new IOException("BIN превышает лимит 256 МиБ");

        List<CatalogItem> items = new ArrayList<>();
        int sourceVersion;

        try (InputStream raw = new InterruptibleInputStream(
                new BoundedInputStream(Files.newInputStream(file), CatalogCodec.MAX_FILE_BYTES));
             ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(raw))) {

            int magic, version;
            try {
                magic = in.readInt();
                version = in.readInt();
            } catch (EOFException ex) {
                throw new MigrationException(MigrationException.Reason.CORRUPTED,
                        "BIN-файл повреждён: заголовок обрезан", ex);
            } catch (IOException ex) {
                throw new IOException("BIN-файл не содержит корректного заголовка", ex);
            }
            if (magic != HEADER_MAGIC)
                throw new IOException("BIN-файл не является каталогом");
            CatalogVersions.validateSupported(version);
            sourceVersion = version;

            Object data;
            try {
                data = in.readObject();
            } catch (InvalidClassException ex) {
                throw new IOException("BIN-файл несовместим с текущей версией: " + ex.getMessage(), ex);
            } catch (ClassNotFoundException ex) {
                throw new IOException("BIN-файл повреждён: неизвестный класс", ex);
            }
            if (in.read() != -1)
                throw new IOException("Лишние данные в конце BIN-файла");
            if (!(data instanceof List<?> list))
                throw new IOException("BIN-файл повреждён: ожидался список");

            for (Object o : list) {
                CatalogCodec.checkCancelled();
                items.add(migrateLoaded(o, version));
            }
        } catch (IOException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new IOException("BIN-файл повреждён: " + ex.getMessage(), ex);
        }

        return new LoadResult<>(List.copyOf(items), sourceVersion, CatalogVersions.CURRENT);
    }

    private static CatalogItem migrateLoaded(Object o, int version) throws IOException {
        return switch (version) {
            case 1 -> migrateV1(o);
            case 2 -> migrateV2(o);
            case 3 -> migrateV3(o);
            default -> throw new MigrationException(MigrationException.Reason.UNSUPPORTED_VERSION,
                    "Неподдерживаемая версия BIN: " + version);
        };
    }

    private static CatalogItem migrateV1(Object o) throws IOException {
        ItemV1Dto d;
        if (o instanceof ItemV1Dto dto) d = dto;
        else if (o instanceof CatalogItem it) d = toV1(it);     // legacy object from old files
        else throw new MigrationException(MigrationException.Reason.CORRUPTED,
                    "Unexpected class in v1 BIN: " + o.getClass().getName());

        return safeCreate(d.type, d.itemNumber, d.productName, d.category, d.price, d.remainder,
                d.startOfWarranty, d.warrantyMonths,
                "", "", 0, Product.DEFAULT_MANUFACTURE_DATE, 1); // <--- дефолт
    }

    private static CatalogItem migrateV2(Object o) throws IOException {
        if (!(o instanceof ItemV2Dto d))
            throw new MigrationException(MigrationException.Reason.CORRUPTED,
                    "Unexpected class in v2 BIN: " + o.getClass().getName());
        return safeCreate(d.type, d.itemNumber, d.productName, d.category, d.price, d.remainder,
                d.startOfWarranty, d.warrantyMonths,
                d.supplier, d.countryOfOrigin, 0, Product.DEFAULT_MANUFACTURE_DATE , 2);
    }

    private static CatalogItem migrateV3(Object o) throws IOException {
        if (!(o instanceof ItemV3Dto d))
            throw new MigrationException(MigrationException.Reason.CORRUPTED,
                    "Unexpected class in v3 BIN: " + o.getClass().getName());
        LocalDate mfg = Product.DEFAULT_MANUFACTURE_DATE;
        if (d.manufactureDate != null && !d.manufactureDate.isEmpty()) {
            try {
                mfg = LocalDate.parse(d.manufactureDate);
            } catch (DateTimeParseException e) {
                throw new MigrationException(MigrationException.Reason.BAD_FIELD,
                        "Некорректная дата manufactureDate: '" + d.manufactureDate + "'...", e);
            }
        }
        return safeCreate(d.type, d.itemNumber, d.productName, d.category, d.price, d.remainder,
                d.startOfWarranty, d.warrantyMonths,
                d.supplier, d.countryOfOrigin, d.discountPercent, mfg, 3);
    }

    private static CatalogItem safeCreate(String type, long id, String name, String category,
                                          long price, int stock, String start, Integer months,
                                          String sup, String cnt, int disc, LocalDate mfg,
                                          int srcVersion) throws IOException {
        try {
            return CatalogCodec.create(type, id, name, category, price, stock, start, months,
                    sup, cnt, disc, mfg);
        } catch (IOException e) {
            throw new MigrationException(MigrationException.Reason.BAD_FIELD,
                    "Ошибка миграции v" + srcVersion + " → v" + CatalogVersions.CURRENT
                            + " (артикул " + id + "): " + e.getMessage(), e);
        }
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted())
            throw new CancellationException("Операция отменена");
    }

    public int getHeaderMagic() { return HEADER_MAGIC; }
    public int getSchemaVersion() { return currentVersion(); }
}