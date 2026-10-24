package lk.thuhina.water.numbering.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One document sequence row (design 4.3 {@code doc_sequence}); read and changed only under a row lock. */
@Entity
@Table(name = "doc_sequence")
public class DocSequence {

    @Id
    @Column(length = 40)
    private String name;

    @Column(nullable = false, length = 10)
    private String prefix;

    @Column(name = "next_value", nullable = false)
    private long nextValue;

    @Column(nullable = false)
    private int padding;

    @Enumerated(EnumType.STRING)
    @Column(name = "reset_rule", nullable = false, length = 10)
    private ResetRule resetRule;

    @Column(name = "period_key", length = 4)
    private String periodKey;

    @Column(length = 100)
    private String description;

    protected DocSequence() {
    }

    /**
     * Takes the next value for the given period: a new period restarts the counter at 1.
     *
     * @return the value to use in the number
     */
    public long take(String newPeriodKey) {
        if (resetRule != ResetRule.NONE && !newPeriodKey.equals(periodKey)) {
            periodKey = newPeriodKey;
            nextValue = 1;
        }
        return nextValue++;
    }

    public String getName() {
        return name;
    }

    public String getPrefix() {
        return prefix;
    }

    public long getNextValue() {
        return nextValue;
    }

    public int getPadding() {
        return padding;
    }

    public ResetRule getResetRule() {
        return resetRule;
    }

    public String getPeriodKey() {
        return periodKey;
    }

    public String getDescription() {
        return description;
    }
}
