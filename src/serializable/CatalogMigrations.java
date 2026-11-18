package serializable;

import model.CatalogItem;
import java.io.IOException;

/**
 * Изолированные шаги миграции каталога между версиями схемы.
 * <p>Каждый шаг — детерминированное преобразование {@link CatalogRecord}:
 * можно вызывать и тестировать по отдельности. {@link #migrateToCurrent} склеивает
 * шаги в цепочку v1 → v2 → v3.</p>
 */
public final class CatalogMigrations {

    private CatalogMigrations() {}

    /** Прогоняет цепочку шагов от {@code fromVersion} до {@link CatalogVersions#CURRENT}. */
    public static void migrateToCurrent(CatalogRecord r, int fromVersion) {
        if (fromVersion < CatalogVersions.V2) migrateV1ToV2(r);
        if (fromVersion < CatalogVersions.V3) migrateV2ToV3(r);
    }

    /** v1 → v2: заполняет поля логистики дефолтами. Не применимо к Discontinued. */
    public static void migrateV1ToV2(CatalogRecord r) {
        if (r.isDiscontinued()) return;
        if (r.supplier == null) r.supplier = "";
        if (r.countryOfOrigin == null) r.countryOfOrigin = "";
    }

    /** v2 → v3: заполняет коммерческие поля дефолтами. Не применимо к Discontinued. */
    public static void migrateV2ToV3(CatalogRecord r) {
        if (r.isDiscontinued()) return;
        if (r.discountPercent == null) r.discountPercent = 0;
        // manufactureDate оставляем null — CatalogCodec подставит DEFAULT.
    }

    /** Собирает итоговый CatalogItem из мигрированной записи. */
    public static CatalogItem toItem(CatalogRecord r) throws IOException {
        return CatalogCodec.fromRecord(r);
    }
}