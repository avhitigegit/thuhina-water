package lk.thuhina.water.numbering.model;

/** When a document sequence starts again from 1 (design 5.3). */
public enum ResetRule {
    /** Never resets: {@code C0001}, {@code B000001}. */
    NONE,
    /** New counter each year; the year is part of the number: {@code PO-2026-0001}. */
    YEARLY,
    /** New counter each month; YYMM is part of the number: {@code INV-2610-0001}. */
    MONTHLY
}
