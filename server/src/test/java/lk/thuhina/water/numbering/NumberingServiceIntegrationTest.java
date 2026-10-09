package lk.thuhina.water.numbering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import lk.thuhina.water.common.BusinessException;
import lk.thuhina.water.numbering.repository.DocSequenceRepository;
import lk.thuhina.water.numbering.service.NumberingService;
import lk.thuhina.water.numbering.service.SequenceNames;
import lk.thuhina.water.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/** Numbering Service against the real database: formats, resets, rollback without gaps, concurrent calls. */
class NumberingServiceIntegrationTest extends IntegrationTest {

    private static final AtomicInteger IDS = new AtomicInteger();

    @Autowired
    NumberingService numbering;
    @Autowired
    DocSequenceRepository sequences;
    @Autowired
    TransactionTemplate tx;

    /** A sequence row only this test uses, so tests do not affect each other. */
    private String testSequence(String prefix, int padding, String resetRule) {
        String name = "TEST_" + resetRule + "_" + IDS.incrementAndGet() + "_" + System.nanoTime() % 1000000;
        jdbc.update("insert into doc_sequence (name, prefix, padding, reset_rule) values (?, ?, ?, ?)", name, prefix, padding, resetRule);
        return name;
    }

    private String next(String name, LocalDate date) {
        return tx.execute(s -> numbering.next(name, date));
    }

    @Test
    void everySequenceFromTheDesignExists() throws IllegalAccessException {
        for (Field f : SequenceNames.class.getFields()) {
            String name = (String) f.get(null);
            assertThat(sequences.findById(name)).as(name).isPresent();
        }
    }

    @Test
    void realSequencesUseTheDesignFormats() {
        String customer = tx.execute(s -> numbering.next(SequenceNames.CUSTOMER));
        String bill = tx.execute(s -> numbering.next(SequenceNames.BILL));
        String invoice = tx.execute(s -> numbering.next(SequenceNames.MONTHLY_INVOICE, LocalDate.of(2099, 9, 30)));
        assertThat(customer).matches("C\\d{4}");
        assertThat(bill).matches("B\\d{6}");
        assertThat(invoice).isEqualTo("INV-9909-0001");
        assertThat(numbering.peek(SequenceNames.CUSTOMER)).matches("C\\d{4}").isNotEqualTo(customer);
    }

    @Test
    void neverResettingSequenceCountsUp() {
        String name = testSequence("C", 4, "NONE");
        assertThat(next(name, LocalDate.of(2026, 10, 5))).isEqualTo("C0001");
        assertThat(next(name, LocalDate.of(2027, 1, 1))).isEqualTo("C0002");
        assertThat(numbering.peek(name)).isEqualTo("C0003");
    }

    @Test
    void yearlySequenceStartsAgainEachYear() {
        String name = testSequence("PO-", 4, "YEARLY");
        assertThat(next(name, LocalDate.of(2026, 1, 1))).isEqualTo("PO-2026-0001");
        assertThat(next(name, LocalDate.of(2026, 12, 31))).isEqualTo("PO-2026-0002");
        assertThat(next(name, LocalDate.of(2027, 1, 1))).isEqualTo("PO-2027-0001");
        assertThat(next(name, LocalDate.of(2027, 6, 1))).isEqualTo("PO-2027-0002");
    }

    @Test
    void monthlySequenceStartsAgainEachMonth() {
        String name = testSequence("INV-", 4, "MONTHLY");
        assertThat(next(name, LocalDate.of(2026, 9, 30))).isEqualTo("INV-2609-0001");
        assertThat(next(name, LocalDate.of(2026, 9, 1))).isEqualTo("INV-2609-0002");
        assertThat(next(name, LocalDate.of(2026, 10, 1))).isEqualTo("INV-2610-0001");
    }

    @Test
    void earlierPeriodIsRefusedBecauseItsNumbersMayExist() {
        String name = testSequence("QT-", 3, "YEARLY");
        next(name, LocalDate.of(2027, 2, 1));
        assertThatThrownBy(() -> next(name, LocalDate.of(2026, 12, 31)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(NumberingService.SEQUENCE_PERIOD_CLOSED);
    }

    @Test
    void rolledBackDocumentLeavesNoGap() {
        String name = testSequence("EX-", 4, "NONE");
        assertThat(next(name, LocalDate.of(2026, 10, 5))).isEqualTo("EX-0001");
        tx.executeWithoutResult(s -> {
            assertThat(numbering.next(name)).isEqualTo("EX-0002");
            s.setRollbackOnly(); // the document save failed
        });
        assertThat(next(name, LocalDate.of(2026, 10, 5))).isEqualTo("EX-0002");
    }

    @Test
    void mustBeCalledInsideTheCallersTransaction() {
        String name = testSequence("X", 3, "NONE");
        assertThatThrownBy(() -> numbering.next(name)).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void concurrentCallsNeverGetTheSameNumberAndLeaveNoGaps() throws Exception {
        String name = testSequence("B", 6, "NONE");
        int threads = 4;
        int perThread = 25;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<String> all = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < perThread; i++) {
                    all.add(next(name, LocalDate.of(2026, 10, 5)));
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        Set<String> unique = new HashSet<>(all);
        assertThat(all).hasSize(threads * perThread);
        assertThat(unique).hasSize(threads * perThread);
        for (int n = 1; n <= threads * perThread; n++) {
            assertThat(unique).contains(String.format("B%06d", n));
        }
    }
}
