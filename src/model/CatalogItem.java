package model;

import java.io.Serializable;

/** Общий read-only контракт для всех сущностей каталога. */
public interface CatalogItem extends Serializable {
    int getItemNumber();
    String getProductName();
    String getCategory();
    int getPrice();
    int getRemainder();
    EntityType getType();
}