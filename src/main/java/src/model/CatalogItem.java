package src.model;

import java.io.Serializable;

public interface CatalogItem extends Serializable {
    int getItemNumber();
    String getProductName();
    String getCategory();
    int getPrice();
    int getRemainder();
    EntityType getType();
}