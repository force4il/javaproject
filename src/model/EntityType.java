package model;

/** Тип сущности в CSV */
public enum EntityType {
    PRODUCT("Product"),
    WARRANTY("Warranty"),
    DISCONTINUED("Discontinued");

    private final String csvName;

    EntityType(String csvName) { this.csvName = csvName; }

    public String getCsvName() { return csvName; }

    public static EntityType fromCsvName(String name) {
        if (name == null) return null;
        for (EntityType t : values())
            if (t.csvName.equals(name)) return t;
        return null;
    }
}