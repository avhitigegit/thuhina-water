package lk.thuhina.water.partners.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lk.thuhina.water.common.BaseEntity;

/** A filling factory (FR-22). Its charge per bottle type is kept as dated {@link FactoryCharge} rows. */
@Entity
@Table(name = "factory")
public class Factory extends BaseEntity {

    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 200)
    private String address;

    @Column(length = 100)
    private String contact;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String email;

    @Column(length = 100)
    private String licence;

    @Column(name = "terms_days", nullable = false)
    private int termsDays;

    @Column(nullable = false)
    private boolean active = true;

    protected Factory() {
    }

    public Factory(String code) {
        this.code = code;
    }

    public void update(String name, String address, String contact, String phone, String email, String licence,
                       int termsDays, boolean active) {
        this.name = name;
        this.address = address;
        this.contact = contact;
        this.phone = phone;
        this.email = email;
        this.licence = licence;
        this.termsDays = termsDays;
        this.active = active;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
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

    public String getLicence() {
        return licence;
    }

    public int getTermsDays() {
        return termsDays;
    }

    public boolean isActive() {
        return active;
    }
}
