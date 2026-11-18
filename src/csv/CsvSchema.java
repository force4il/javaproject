package csv;

import model.EntityType;
import serializable.CatalogVersions;

public final class CsvSchema {
    public static final int MIN_VERSION = CatalogVersions.MIN_SUPPORTED;
    public static final int CURRENT_VERSION = CatalogVersions.CURRENT;

    private CsvSchema() {}

    /** Кол-во колонок для строки данного типа и версии*/
    public static int expectedColumns(EntityType type, int version) {
        int n = 6;
        if (type == EntityType.WARRANTY)
            n += 2;

        if (version >= 2 && type != EntityType.DISCONTINUED)
            n += 2;

        if (version >= 3 && type != EntityType.DISCONTINUED)
            n += 2;

        return n;
    }
}