package serializable;

import model.CatalogItem;
import model.Product;
import java.io.IOException;

public final class CatalogMigrations {

    private CatalogMigrations() {}

    public static void migrateToCurrent(CatalogRecord r, int fromVersion) {
        if (fromVersion < CatalogVersions.V2)
            migrateV1ToV2(r);

        if (fromVersion < CatalogVersions.V3)
            migrateV2ToV3(r);
    }

    public static void migrateV1ToV2(CatalogRecord r) {
        if (r.isDiscontinued())
            return;

        if (r.supplier == null)
            r.supplier = "";

        if (r.countryOfOrigin == null)
            r.countryOfOrigin = "";
    }

    public static void migrateV2ToV3(CatalogRecord r) {
        if (r.isDiscontinued())
            return;

        if (r.discountPercent == null)
            r.discountPercent = 0;

        if (r.manufactureDate == null)
            r.manufactureDate = Product.DEFAULT_MANUFACTURE_DATE.toString();
    }

    public static CatalogItem toItem(CatalogRecord r) throws IOException {
        return CatalogCodec.fromRecord(r);
    }
}