package lk.thuhina.water.masterdata.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lk.thuhina.water.common.BaseEntity;

/**
 * An old-bottle brand that is accepted in place of the deposit for one bottle (FR-06, BR-02), e.g. American Water
 * for 20L. Never deleted – "Stop accepting" makes it inactive.
 */
@Entity
@Table(name = "old_bottle_brand")
public class OldBottleBrand extends BaseEntity {

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "bottle_type_code", nullable = false, length = 10)
    private String bottleTypeCode;

    @Column(length = 200)
    private String note;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "added_on", nullable = false)
    private LocalDate addedOn;

    protected OldBottleBrand() {
    }

    public OldBottleBrand(String name, String bottleTypeCode, String note, LocalDate addedOn) {
        this.name = name;
        this.bottleTypeCode = bottleTypeCode;
        this.note = note;
        this.addedOn = addedOn;
    }

    public void update(String bottleTypeCode, String note, boolean active) {
        this.bottleTypeCode = bottleTypeCode;
        this.note = note;
        this.active = active;
    }

    public String getName() {
        return name;
    }

    public String getBottleTypeCode() {
        return bottleTypeCode;
    }

    public String getNote() {
        return note;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDate getAddedOn() {
        return addedOn;
    }
}
