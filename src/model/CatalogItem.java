package model;

/**Общий read-only контракт для всех сущностей каталога*/
public interface CatalogItem {
    int getItemNumber();
    String getProductName();
    String getCategory();
    int getPrice();
    int getRemainder();
    EntityType getType();
}