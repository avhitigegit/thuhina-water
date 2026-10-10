package lk.thuhina.water.ledger.model;

import java.time.LocalDate;
import java.util.List;

/**
 * A posting to the ledger: the document that caused it ({@code sourceType} + {@code sourceId}, shown as {@code docNo}),
 * the business date, a readable description for the Movements tab, and its effects.
 */
public record Posting(SourceType sourceType, Long sourceId, String docNo, LocalDate date, String description,
                      List<Effect> effects) {

    public Posting {
        effects = List.copyOf(effects);
    }
}
