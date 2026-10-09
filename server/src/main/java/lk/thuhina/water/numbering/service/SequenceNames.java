package lk.thuhina.water.numbering.service;

/** Names of the rows in {@code doc_sequence} (created by V001__foundation.sql, design 5.3). */
public final class SequenceNames {

    public static final String CUSTOMER = "CUSTOMER";                       // C0001
    public static final String PRODUCT = "PRODUCT";                         // P01
    public static final String SUPPLIER = "SUPPLIER";                       // S01
    public static final String FACTORY = "FACTORY";                         // F01
    public static final String QUOTATION_REQUEST = "QUOTATION_REQUEST";     // QR-2026-001
    public static final String SUPPLIER_QUOTATION = "SUPPLIER_QUOTATION";   // SQ-0001
    public static final String CUSTOMER_QUOTATION = "CUSTOMER_QUOTATION";   // QT-2026-001
    public static final String PURCHASE_ORDER = "PURCHASE_ORDER";           // PO-2026-0001
    public static final String GOODS_RECEIPT = "GOODS_RECEIPT";             // GRN-0001
    public static final String FACTORY_DISPATCH = "FACTORY_DISPATCH";       // FD-0001
    public static final String FACTORY_BATCH = "FACTORY_BATCH";             // FB-0001
    public static final String FACTORY_RETURN = "FACTORY_RETURN";           // FR-0001
    public static final String FACTORY_PAYMENT = "FACTORY_PAYMENT";         // FP-0001
    public static final String BILL = "BILL";                               // B000001
    public static final String RECEIPT = "RECEIPT";                         // RC-00001
    public static final String MONTHLY_INVOICE = "MONTHLY_INVOICE";         // INV-2610-0001
    public static final String STOCK_ADJUSTMENT = "STOCK_ADJUSTMENT";       // ADJ-0001
    public static final String SUPPLIER_PAYMENT = "SUPPLIER_PAYMENT";       // SP-0001
    public static final String EXPENSE = "EXPENSE";                         // EX-0001
    public static final String CUSTOMER_PRICE = "CUSTOMER_PRICE";           // CP0001

    private SequenceNames() {
    }
}
