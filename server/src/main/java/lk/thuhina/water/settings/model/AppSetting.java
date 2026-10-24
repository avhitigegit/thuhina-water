package lk.thuhina.water.settings.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;

/** One settings group stored as a JSON object (design 4.3 {@code app_setting}): company, business, features … */
@Entity
@Table(name = "app_setting")
public class AppSetting {

    @Id
    @Column(name = "key", length = 50)
    private String key;

    @Column(name = "value", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String value;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    protected AppSetting() {
    }

    public AppSetting(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public void update(String json, Instant at, String by) {
        this.value = json;
        this.updatedAt = at;
        this.updatedBy = by;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }
}
