package serializable;

import exceptions.MigrationException;
import model.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;

public final class BinaryCatalogSerializer implements Serializer<List<CatalogItem>> {

    private static final int HEADER_MAGIC = 0x42434154;

    private static final ObjectInputFilter CATALOG_FILTER = info -> {
        if (info.depth() > 30 || info.references() > 2_000_000 || info.arrayLength() > CatalogCodec.MAX_ITEMS)
            return ObjectInputFilter.Status.REJECTED;

        Class<?> cls = info.serialClass();

        if (cls == null)
            return ObjectInputFilter.Status.UNDECIDED;

        if (cls.isArray()) {
            Class<?> component = cls;
            while (component.isArray())
                component = component.getComponentType();

            if (component.isPrimitive() || component == Object.class || component == String.class)
                return ObjectInputFilter.Status.ALLOWED;

            return ObjectInputFilter.Status.REJECTED;
        }

        if (cls == ItemV1Dto.class || cls == ItemV2Dto.class || cls == ItemV3Dto.class
                || cls == ArrayList.class || cls == String.class || cls == Object.class
                || cls == Number.class || cls == Integer.class || cls == Long.class) {
            return ObjectInputFilter.Status.ALLOWED;
        }

        return ObjectInputFilter.Status.REJECTED;
    };


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
                out.writeObject(toDos(items, targetVersion));
            }
            checkCancelled();
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                throw new IOException("Файловая система не поддерживает атомарное сохранение", ex);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static List<Object> toDos(List<CatalogItem> items, int version) {
        List<Object> out = new ArrayList<>(items.size());
        for (CatalogItem it : items) {
            CatalogCodec.checkCancelled();
            out.add(toDto(CatalogRecord.from(it), version));
        }
        return out;
    }

    private static Object toDto(CatalogRecord r, int version) {
        String sup = nullToEmpty(r.supplier);
        String cnt = nullToEmpty(r.countryOfOrigin);
        int discount = r.discountPercent == null ? 0 : r.discountPercent;

        return switch (version) {
            case 1 -> new ItemV1Dto(r.type, r.itemNumber, r.productName, r.category,
                    r.price, r.remainder, r.startOfWarranty, r.warrantyMonths);
            case 2 -> new ItemV2Dto(r.type, r.itemNumber, r.productName, r.category,
                    r.price, r.remainder, r.startOfWarranty, r.warrantyMonths,
                    sup, cnt);
            case 3 -> new ItemV3Dto(r.type, r.itemNumber, r.productName, r.category,
                    r.price, r.remainder, r.startOfWarranty, r.warrantyMonths,
                    sup, cnt, discount, r.manufactureDate);
            default -> throw new IllegalStateException("bad version " + version);
        };
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

            in.setObjectInputFilter(CATALOG_FILTER);
            int magic, version;
            try {
                magic = in.readInt();
                version = in.readInt();
            } catch (EOFException ex) {
                throw new MigrationException(MigrationException.Reason.CORRUPTED,
                        "BIN-файл повреждён: заголовок обрезан", ex);
            } catch (IOException ex) {
                throw new MigrationException(MigrationException.Reason.CORRUPTED,
                        "BIN-файл не содержит корректного заголовка", ex);
            }
            if (magic != HEADER_MAGIC) {
                throw new MigrationException(MigrationException.Reason.CORRUPTED,
                        "BIN-файл не является каталогом");
            }
            CatalogVersions.validateSupported(version);
            sourceVersion = version;

            Object data;
            try {
                data = in.readObject();
            } catch (InvalidClassException ex) {
                throw new MigrationException(MigrationException.Reason.CORRUPTED,
                        "BIN-файл содержит несовместимые или запрещённые классы", ex);
            } catch (ClassNotFoundException ex) {
                throw new MigrationException(MigrationException.Reason.CORRUPTED,
                        "BIN-файл содержит неизвестный класс", ex);
            }

            if (in.read() != -1)
                throw new MigrationException(MigrationException.Reason.CORRUPTED,
                        "Лишние данные в конце BIN-файла");

            if (!(data instanceof List<?> list))
                throw new MigrationException(MigrationException.Reason.CORRUPTED,
                        "BIN-файл повреждён: ожидался список");


            if (list.size() > CatalogCodec.MAX_ITEMS)
                throw new MigrationException(MigrationException.Reason.CORRUPTED,
                        "Превышен лимит " + CatalogCodec.MAX_ITEMS + " записей");

            Set<Integer> seenKeys = new HashSet<>();
            for (Object o : list) {
                CatalogCodec.checkCancelled();
                CatalogItem item = migrateAndCreate(toRecord(o, version), version);
                if (!seenKeys.add(item.getItemNumber()))
                    throw new MigrationException(MigrationException.Reason.CORRUPTED,
                            "Повтор артикула в BIN: " + item.getItemNumber());

                items.add(item);
            }
        } catch (MigrationException ex) {
            throw ex;
        } catch (IOException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new MigrationException(MigrationException.Reason.CORRUPTED,
                    "BIN-файл повреждён: " + ex.getMessage(), ex);
        }

        return new LoadResult<>(List.copyOf(items), sourceVersion, CatalogVersions.CURRENT);
    }

    private static CatalogRecord toRecord(Object o, int version) throws IOException {
        return switch (version) {
            case 1 -> fromV1(o);
            case 2 -> fromV2(o);
            case 3 -> fromV3(o);
            default -> throw new MigrationException(MigrationException.Reason.UNSUPPORTED_VERSION,
                    "Неподдерживаемая версия BIN: " + version);
        };
    }

    private static CatalogRecord baseFromDto(String type, long id, String name, String cat,
                                             long price, int rem, String start, Integer months) {
        CatalogRecord r = new CatalogRecord();
        r.type = type;
        r.itemNumber = id;
        r.productName = name;
        r.category = cat;
        r.price = price;
        r.remainder = rem;
        r.startOfWarranty = start;
        r.warrantyMonths = months;

        return r;
    }

    private static CatalogRecord fromV1(Object o) throws IOException {
        if (o instanceof CatalogItem it)
            return CatalogRecord.from(it);

        if (!(o instanceof ItemV1Dto(
                String type, long itemNumber, String productName, String category, long price, int remainder,
                String startOfWarranty, Integer warrantyMonths
        )))
            throw new MigrationException(MigrationException.Reason.CORRUPTED,
                    "Unexpected class in v1 BIN: " + (o == null ? "null" : o.getClass().getName()));

        return baseFromDto(type, itemNumber, productName, category,
                price, remainder, startOfWarranty, warrantyMonths);
    }

    private static CatalogRecord fromV2(Object o) throws IOException {
        if (!(o instanceof ItemV2Dto(
                String type, long itemNumber, String productName, String category, long price, int remainder,
                String startOfWarranty, Integer warrantyMonths, String supplier, String countryOfOrigin
        )))
            throw new MigrationException(MigrationException.Reason.CORRUPTED,
                    "Unexpected class in v2 BIN: " + (o == null ? "null" : o.getClass().getName()));

        CatalogRecord r = baseFromDto(type, itemNumber, productName, category,
                price, remainder, startOfWarranty, warrantyMonths);
        r.supplier = supplier;
        r.countryOfOrigin = countryOfOrigin;

        return r;
    }

    private static CatalogRecord fromV3(Object o) throws IOException {
        if (!(o instanceof ItemV3Dto(
                String type, long itemNumber, String productName, String category, long price, int remainder,
                String startOfWarranty, Integer warrantyMonths, String supplier, String countryOfOrigin,
                int discountPercent, String manufactureDate
        )))
            throw new MigrationException(MigrationException.Reason.CORRUPTED,
                    "Unexpected class in v3 BIN: " + (o == null ? "null" : o.getClass().getName()));

        CatalogRecord r = baseFromDto(type, itemNumber, productName, category,
                price, remainder, startOfWarranty, warrantyMonths);
        r.supplier = supplier;
        r.countryOfOrigin = countryOfOrigin;
        r.discountPercent = discountPercent;
        r.manufactureDate = manufactureDate;

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

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted())
            throw new CancellationException("Операция отменена");
    }

}