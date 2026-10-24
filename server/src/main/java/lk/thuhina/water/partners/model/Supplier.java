package lk.thuhina.water.partners.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lk.thuhina.water.common.BaseEntity;

/** A supplier of empty bottles and / or products (FR-14), with the items it supplies and its payment terms. */
@Entity
@Table(name = "supplier")
public class Supplier extends BaseEntity {

    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String contact;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 100)
    private String email;

    @Column(length = 200)
    private String address;

    @Column(name = "terms_days", nullable = false)
    private int termsDays;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "supplier", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("itemType ASC, itemCode ASC")
    private List<SupplierItem> items = new ArrayList<>();

    protected Supplier() {
    }

    public Supplier(String code) {
        this.code = code;
    }

    public void update(String name, String contact, String phone, String email, String address, int termsDays, boolean active) {
        this.name = name;
        this.contact = contact;
        this.phone = phone;
        this.email = email;
        this.address = address;
        this.termsDays = termsDays;
        this.active = active;
    }

    /** Replaces the supplied items (rows not in the new list are removed). */
    public void replaceItems(Collection<SupplierItem.Ref> refs) {
        items.removeIf(i -> !refs.contains(i.ref()));
        for (SupplierItem.Ref r : refs) {
            if (items.stream().noneMatch(i -> i.ref().equals(r))) {
                items.add(new SupplierItem(this, r.type(), r.code()));
            }
        }
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getContact() {
        return contact;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getAddress() {
        return address;
    }

    public int getTermsDays() {
        return termsDays;
    }

    public boolean isActive() {
        return active;
    }

    public List<SupplierItem> getItems() {
        return items;
    }
}
