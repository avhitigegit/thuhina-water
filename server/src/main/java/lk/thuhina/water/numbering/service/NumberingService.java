package lk.thuhina.water.numbering.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;

import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.BusinessException;
import lk.thuhina.water.numbering.model.DocSequence;
import lk.thuhina.water.numbering.model.ResetRule;
import lk.thuhina.water.numbering.repository.DocSequenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Numbering Service (design 5.3, FR-60, BR-17). Every document number and code comes from here.
 * <p>
 * {@code next(name)} locks the sequence row ({@code SELECT … FOR UPDATE}) <b>in the caller's transaction</b>
 * ({@link Propagation#MANDATORY}): if the document save fails, the number is rolled back too, so numbers
 * stay unique and gap-free. Two users saving at the same moment wait for each other on the row lock.
 */
@Service
public class NumberingService {

    public static final String SEQUENCE_PERIOD_CLOSED = "SEQUENCE_PERIOD_CLOSED";

    private final DocSequenceRepository repository;
    private final Clock clock;

    public NumberingService(DocSequenceRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Next number using today's date for yearly / monthly sequences. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(String name) {
        return next(name, BusinessDates.today(clock));
    }

    /**
     * Next number for the period of {@code date} – e.g. the monthly invoice run passes the invoice period.
     * Sequences only move forward: asking for a period before the current one is refused, because its
     * numbers may already be used.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(String name, LocalDate date) {
        DocSequence seq = repository.lockByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Unknown document sequence: " + name));
        String period = SequenceFormat.periodKey(seq.getResetRule(), date);
        if (seq.getResetRule() != ResetRule.NONE && seq.getPeriodKey() != null && period.compareTo(seq.getPeriodKey()) < 0) {
            throw new BusinessException(SEQUENCE_PERIOD_CLOSED,
                    "Numbers for an earlier period can no longer be created.",
                    Map.of("sequence", name, "period", period, "currentPeriod", seq.getPeriodKey()));
        }
        long value = seq.take(period);
        return SequenceFormat.format(seq.getPrefix(), seq.getResetRule(), period, value, seq.getPadding());
    }

    /** The number the next call would return (for "will be created as …" hints); does not use it up. */
    @Transactional(readOnly = true)
    public String peek(String name) {
        DocSequence seq = repository.findById(name)
                .orElseThrow(() -> new IllegalArgumentException("Unknown document sequence: " + name));
        String period = SequenceFormat.periodKey(seq.getResetRule(), BusinessDates.today(clock));
        long value = seq.getResetRule() != ResetRule.NONE && !period.equals(seq.getPeriodKey()) ? 1 : seq.getNextValue();
        return SequenceFormat.format(seq.getPrefix(), seq.getResetRule(), period, value, seq.getPadding());
    }
}
