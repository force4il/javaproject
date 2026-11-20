package serializable;

import exceptions.MigrationException;

public final class CatalogVersions {
    public static final int V1 = 1;
    public static final int V2 = 2;
    public static final int V3 = 3;
    public static final int CURRENT = V3;
    public static final int MIN_SUPPORTED = V1;

    private CatalogVersions() {}

    public static void validateSupported(int version) throws MigrationException {
        if (version < MIN_SUPPORTED)
            throw new MigrationException(MigrationException.Reason.UNSUPPORTED_VERSION,
                    "Версия схемы " + version + " не поддерживается (min=" + MIN_SUPPORTED + ")");
        if (version > CURRENT)
            throw new MigrationException(MigrationException.Reason.FUTURE_VERSION,
                    "Файл создан в более новой версии приложения: v" + version
                            + " > v" + CURRENT + ". Обновите приложение или сохраните файл в текущей версии.");
    }

    public static String lossFromTo(int from, int to) {
        if (from <= to)
            return "";

        StringBuilder sb = new StringBuilder();
        if (from >= V2 && to < V2)
            sb.append(" supplier (поставщик)\n• countryOfOrigin (страна происхождения)\n");

        if (from >= V3 && to < V3)
            sb.append(" discountPercent (скидка, %)\n• manufactureDate (дата изготовления)\n");

        return sb.toString();
    }
}