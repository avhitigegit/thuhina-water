package lk.thuhina.water.masterdata.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lk.thuhina.water.common.BaseEntity;

/**
 * Customer type (FR-03) – Household, Shop, Office, Factory … The water price depends on it. The name cannot be changed
 * after creation (as in the prototype), so price lists and confirmed-price keys stay stable.
 */
@Entity
@Table(name = "customer_type")
public class CustomerType extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 200)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    protected CustomerType() {
    }

    public CustomerType(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void update(String description, boolean active) {
        this.description = description;
        this.active = active;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }
}
