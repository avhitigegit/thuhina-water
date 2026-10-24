package lk.thuhina.water.expenses.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lk.thuhina.water.common.BaseEntity;

/** A kind of running expense (FR-63): Electricity, Salaries, … An inactive type is hidden when adding; its expenses stay. */
@Entity
@Table(name = "expense_type")
public class ExpenseType extends BaseEntity {

    @Column(nullable = false, length = 60)
    private String name;

    @Column(length = 200)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    protected ExpenseType() {
    }

    public ExpenseType(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void update(String name, String description, boolean active) {
        this.name = name;
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
