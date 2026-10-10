package lk.thuhina.water.masterdata.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lk.thuhina.water.common.BaseEntity;

/** Delivery area – also the delivery route. Managed from the Customers page (M05); the API is here. */
@Entity
@Table(name = "area")
public class Area extends BaseEntity {

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    protected Area() {
    }

    public Area(String name) {
        this.name = name;
    }

    public void update(String name, boolean active) {
        this.name = name;
        this.active = active;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }
}
