package model;

import java.util.Optional;

/** Тип сущности в CSV */
public enum EntityType {
    PRODUCT("Product"),
    WARRANTY("Warranty"),
    DISCONTINUED("Discontinued");

    private final String csvName;

    EntityType(String csvName) { this.csvName = csvName; }

    public String getCsvName() { return csvName; }

    public static Optional<EntityType> fromCsvName(String name) {
        if (name == null)
            return Optional.empty();
        for (EntityType t : values())
            if (t.csvName.equals(name))
                return Optional.of(t);

        return Optional.empty();
    }
}