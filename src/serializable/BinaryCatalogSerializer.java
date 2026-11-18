package serializable;

import model.*;

import java.io.*;
import java.nio.file.*;
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
        CatalogRecord r;
        if (o instanceof ItemV1Dto d) {
            r = baseRecord(d.type, d.itemNumber, d.productName, d.category, d.price, d.remainder);
            r.startOfWarranty = d.startOfWarranty;
            r.warrantyMonths = d.warrantyMonths;
        } else if (o instanceof CatalogItem it) {
            r = fromLegacyV1(it);
        } else {
            throw new MigrationException(MigrationException.Reason.CORRUPTED,
                    "Unexpected class in v1 BIN: " + o.getClass().getName());
        }
        return migrateAndCreate(r, CatalogVersions.V1);
    }

    private static CatalogItem migrateV2(Object o) throws IOException {
        if (!(o instanceof ItemV2Dto d))
            throw new MigrationException(MigrationException.Reason.CORRUPTED,
                    "Unexpected class in v2 BIN: " + o.getClass().getName());
        CatalogRecord r = baseRecord(d.type, d.itemNumber, d.productName, d.category, d.price, d.remainder);
        r.startOfWarranty = d.startOfWarranty;
        r.warrantyMonths = d.warrantyMonths;
        r.supplier = d.supplier;
        r.countryOfOrigin = d.countryOfOrigin;
        return migrateAndCreate(r, CatalogVersions.V2);
    }

    private static CatalogItem migrateV3(Object o) throws IOException {
        if (!(o instanceof ItemV3Dto d))
            throw new MigrationException(MigrationException.Reason.CORRUPTED,
                    "Unexpected class in v3 BIN: " + o.getClass().getName());
        CatalogRecord r = baseRecord(d.type, d.itemNumber, d.productName, d.category, d.price, d.remainder);
        r.startOfWarranty = d.startOfWarranty;
        r.warrantyMonths = d.warrantyMonths;
        r.supplier = d.supplier;
        r.countryOfOrigin = d.countryOfOrigin;
        r.discountPercent = d.discountPercent;
        r.manufactureDate = d.manufactureDate;
        return migrateAndCreate(r, CatalogVersions.V3);
    }

    private static CatalogRecord baseRecord(String type, long id, String name, String cat, long price, int rem) {
        CatalogRecord r = new CatalogRecord();
        r.type = type; r.itemNumber = id; r.productName = name; r.category = cat;
        r.price = price; r.remainder = rem;
        return r;
    }

    private static CatalogRecord fromLegacyV1(CatalogItem it) {
        CatalogRecord r = baseRecord(it.getType().getCsvName(), it.getItemNumber(),
                it.getProductName(), it.getCategory(), it.getPrice(), it.getRemainder());
        if (it instanceof ProductWithWarranty w) {
            r.startOfWarranty = w.getStartOfWarranty() == null ? null : w.getStartOfWarranty().toString();
            r.warrantyMonths = w.getWarrantyMonths();
        }
        return r;
    }

    private static CatalogItem migrateAndCreate(CatalogRecord r, int srcVersion) throws IOException {
        CatalogMigrations.migrateToCurrent(r, srcVersion);
        try {
            return CatalogMigrations.toItem(r);
        } catch (MigrationException e) {
            throw e;
        } catch (IOException e) {
            throw new MigrationException(MigrationException.Reason.BAD_FIELD,
                    "Ошибка миграции v" + srcVersion + " → v" + CatalogVersions.CURRENT
                            + " (артикул " + r.itemNumber + "): " + e.getMessage(), e);
        }
    }

    private static ItemV1Dto toV1(CatalogItem it) {
        CatalogRecord r = CatalogRecord.from(it);
        return new ItemV1Dto(r.type, r.itemNumber, r.productName, r.category, r.price, r.remainder,
                r.startOfWarranty, r.warrantyMonths);
    }

    private static ItemV2Dto toV2(CatalogItem it) {
        CatalogRecord r = CatalogRecord.from(it);
        return new ItemV2Dto(r.type, r.itemNumber, r.productName, r.category, r.price, r.remainder,
                r.startOfWarranty, r.warrantyMonths,
                r.supplier == null ? "" : r.supplier,
                r.countryOfOrigin == null ? "" : r.countryOfOrigin);
    }

    private static ItemV3Dto toV3(CatalogItem it) {
        CatalogRecord r = CatalogRecord.from(it);
        return new ItemV3Dto(r.type, r.itemNumber, r.productName, r.category, r.price, r.remainder,
                r.startOfWarranty, r.warrantyMonths,
                r.supplier == null ? "" : r.supplier,
                r.countryOfOrigin == null ? "" : r.countryOfOrigin,
                r.discountPercent == null ? 0 : r.discountPercent,
                r.manufactureDate);
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted())
            throw new CancellationException("Операция отменена");
    }

    public int getHeaderMagic() { return HEADER_MAGIC; }
    public int getSchemaVersion() { return currentVersion(); }
}