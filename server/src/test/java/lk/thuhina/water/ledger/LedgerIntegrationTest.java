package lk.thuhina.water.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import lk.thuhina.water.common.BusinessException;
import lk.thuhina.water.ledger.model.Bucket;
import lk.thuhina.water.ledger.model.Effect;
import lk.thuhina.water.ledger.model.Posting;
import lk.thuhina.water.ledger.model.SourceType;
import lk.thuhina.water.ledger.service.LedgerService;
import lk.thuhina.water.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** Ledger Engine (M04 S2, S8): posting, BR-12, reverse, movements, and concurrency. */
class LedgerIntegrationTest extends IntegrationTest {

    /** Bottle sizes 400.01, 400.02 … – only this class uses them. */
    private static final AtomicInteger SIZE = new AtomicInteger();
    private static final AtomicInteger SOURCE = new AtomicInteger(1_000_000);
    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

    @Autowired
    LedgerService ledger;
    @Autowired
    TransactionTemplate tx;

    private String bottle;
    private String label;

    @BeforeEach
    void bottle() {
        BigDecimal litres = new BigDecimal("400").add(BigDecimal.valueOf(SIZE.incrementAndGet(), 2));
        bottle = "B" + litres.stripTrailingZeros().toPlainString().replace('.', '_');
        label = litres.stripTrailingZeros().toPlainString() + "L";
        // The trigger creates the five stock rows of a new bottle type.
        jdbc.update("insert into bottle_type (code, name, litres) values (?, ?, ?)", bottle, label + " Ledger", litres);
    }

    private long source() {
        return SOURCE.incrementAndGet();
    }

    private void post(SourceType type, long id, String doc, Effect... effects) {
        tx.executeWithoutResult(s -> ledger.post(new Posting(type, id, doc, DAY, "Test " + doc, List.of(effects))));
    }

    private int qty(Bucket b) {
        return ledger.balances(bottle).get(b);
    }

    @Test
    void aNewBottleTypeGetsFiveEmptyBuckets() {
        assertThat(ledger.balances(bottle)).containsOnlyKeys(Bucket.values()).allSatisfy((b, q) -> assertThat(q).isZero());
    }

    @Test
    void postingChangesBucketsAndWritesEffectsAndMovements() {
        long id = source();
        post(SourceType.IMPORT, id, "OPEN-" + id, Effect.stock(bottle, Bucket.FILLED, 10), Effect.stock(bottle, Bucket.EMPTY, 4),
                Effect.stock(bottle, Bucket.FILLED, 2)); // two effects on one row are added up
        assertThat(qty(Bucket.FILLED)).isEqualTo(12);
        assertThat(qty(Bucket.EMPTY)).isEqualTo(4);
        assertThat(ledger.effectsOf(SourceType.IMPORT, id)).containsExactlyInAnyOrder(
                Effect.stock(bottle, Bucket.EMPTY, 4), Effect.stock(bottle, Bucket.FILLED, 12));
        assertThat(jdbc.queryForObject("select count(*) from stock_movement where source_type = 'IMPORT' and source_id = ?",
                Integer.class, id)).isEqualTo(2);
    }

    @Test
    void goingBelowZeroIsRefusedWithTheBr12MessageAndNothingChanges() {
        long id = source();
        post(SourceType.IMPORT, id, "OPEN", Effect.stock(bottle, Bucket.FILLED, 5));
        long dmg = source();
        assertThatThrownBy(() -> post(SourceType.DAMAGE, dmg, "DMG",
                Effect.stock(bottle, Bucket.FILLED, -8), Effect.stock(bottle, Bucket.WRITTEN_OFF, 8)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Filled in store (" + label + ") cannot go below zero – available 5, needed 8. Record a stock adjustment first.")
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("STOCK_NEGATIVE");
        assertThat(qty(Bucket.FILLED)).isEqualTo(5);
        assertThat(qty(Bucket.WRITTEN_OFF)).isZero();
        assertThat(ledger.effectsOf(SourceType.DAMAGE, dmg)).isEmpty();
    }

    @Test
    void reverseRestoresTheValues() {
        post(SourceType.IMPORT, source(), "OPEN", Effect.stock(bottle, Bucket.EMPTY, 20));
        long batch = source();
        post(SourceType.BATCH, batch, "FB-T", Effect.stock(bottle, Bucket.EMPTY, -12), Effect.stock(bottle, Bucket.FACTORY, 12));
        assertThat(qty(Bucket.EMPTY)).isEqualTo(8);
        assertThat(qty(Bucket.FACTORY)).isEqualTo(12);

        tx.executeWithoutResult(s -> ledger.reverse(SourceType.BATCH, batch,
                new Posting(SourceType.BATCH, source(), "FB-T-R", DAY, "Reversal of FB-T", List.of())));
        assertThat(qty(Bucket.EMPTY)).isEqualTo(20);
        assertThat(qty(Bucket.FACTORY)).isZero();
    }

    @Test
    void aReverseThatWouldGoNegativeIsRefused() {
        long open = source();
        post(SourceType.IMPORT, open, "OPEN", Effect.stock(bottle, Bucket.FILLED, 3));
        post(SourceType.DAMAGE, source(), "DMG", Effect.stock(bottle, Bucket.FILLED, -2), Effect.stock(bottle, Bucket.WRITTEN_OFF, 2));
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> ledger.reverse(SourceType.IMPORT, open,
                new Posting(SourceType.IMPORT, source(), "OPEN-R", DAY, "Undo opening", List.of()))))
                .hasMessageContaining("Filled in store (" + label + ") cannot go below zero – available 1, needed 3.");
    }

    @Test
    void productStockGoesThroughTheLedgerToo() {
        String code = "PT" + (SIZE.get() % 1000);
        jdbc.update("insert into product (code, name, selling_price, stock_qty) values (?, 'Ledger Pump', 100, 2)", code);
        assertThatThrownBy(() -> post(SourceType.SALES_TXN, source(), "B-T", Effect.product(code, -3)))
                .hasMessage("Ledger Pump stock cannot go below zero – available 2, needed 3. Record a stock adjustment first.");
        post(SourceType.SALES_TXN, source(), "B-T", Effect.product(code, -2));
        assertThat(jdbc.queryForObject("select stock_qty from product where code = ?", Integer.class, code)).isZero();
    }

    @Test
    void postingNeedsTheCallersTransaction() {
        assertThatThrownBy(() -> ledger.post(new Posting(SourceType.IMPORT, 1L, "X", DAY, "x",
                List.of(Effect.stock(bottle, Bucket.EMPTY, 1)))))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test
    void ledgerRowsCannotBeChangedOrDeleted() {
        post(SourceType.IMPORT, source(), "OPEN", Effect.stock(bottle, Bucket.EMPTY, 1));
        assertThatThrownBy(() -> jdbc.update("update ledger_effect set delta = 5 where bottle_type_code = ?", bottle))
                .hasMessageContaining("insert-only");
        assertThatThrownBy(() -> jdbc.update("delete from stock_movement where item_code = ?", bottle))
                .hasMessageContaining("insert-only");
        // Last guard: the database itself refuses a negative balance.
        assertThatThrownBy(() -> jdbc.update("update stock_balance set qty = -1 where bottle_type_code = ? and bucket = 'EMPTY'", bottle))
                .hasMessageContaining("ck_stock_balance_qty");
    }

    /**
     * Concurrency (design 5.1): 20 users take one filled bottle each when only 10 are left – exactly 10 succeed,
     * the rest get STOCK_NEGATIVE, and the balance ends at 0 (never negative, no lost update).
     */
    @Test
    void parallelPostingsNeverGoNegativeOrLoseUpdates() throws Exception {
        post(SourceType.IMPORT, source(), "OPEN", Effect.stock(bottle, Bucket.FILLED, 10));
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            long id = source();
            results.add(pool.submit(() -> {
                start.await();
                try {
                    post(SourceType.SALES_TXN, id, "B-" + id, Effect.stock(bottle, Bucket.FILLED, -1),
                            Effect.stock(bottle, Bucket.CUSTOMERS, 1));
                    return true;
                } catch (BusinessException e) {
                    assertThat(e.getCode()).isEqualTo("STOCK_NEGATIVE");
                    return false;
                }
            }));
        }
        start.countDown();
        int ok = 0;
        for (Future<Boolean> f : results) {
            if (f.get(60, TimeUnit.SECONDS)) {
                ok++;
            }
        }
        pool.shutdown();
        assertThat(ok).isEqualTo(10);
        assertThat(ledger.balances(bottle)).contains(Map.entry(Bucket.FILLED, 0), Map.entry(Bucket.CUSTOMERS, 10));

        // And 20 parallel additions to one row all count.
        ExecutorService pool2 = Executors.newFixedThreadPool(threads);
        CountDownLatch start2 = new CountDownLatch(1);
        List<Future<?>> adds = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            long id = source();
            adds.add(pool2.submit(() -> {
                start2.await();
                post(SourceType.GRN, id, "GRN-" + id, Effect.stock(bottle, Bucket.EMPTY, 1));
                return null;
            }));
        }
        start2.countDown();
        for (Future<?> f : adds) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool2.shutdown();
        assertThat(qty(Bucket.EMPTY)).isEqualTo(20);
    }
}
