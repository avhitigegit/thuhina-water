package lk.thuhina.water.purchasing.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One status change of a PO: when, by whom, and a note (cancel reason, GRN number). Insert-only (database trigger). */
@Entity
@Table(name = "po_status_history")
public class PoStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "po_id", updatable = false)
    private PurchaseOrder po;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PoStatus status;

    @Column(nullable = false, updatable = false)
    private Instant ts;

    @Column(nullable = false, length = 50, updatable = false)
    private String username;

    @Column(length = 300, updatable = false)
    private String note;

    protected PoStatusHistory() {
    }

    PoStatusHistory(PurchaseOrder po, PoStatus status, Instant ts, String username, String note) {
        this.po = po;
        this.status = status;
        this.ts = ts;
        this.username = username;
        this.note = note;
    }

    public PoStatus getStatus() {
        return status;
    }

    public Instant getTs() {
        return ts;
    }

    public String getUsername() {
        return username;
    }

    public String getNote() {
        return note;
    }
}
