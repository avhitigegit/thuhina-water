package lk.thuhina.water.whatsapp.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import lk.thuhina.water.whatsapp.model.MessageStatus;
import lk.thuhina.water.whatsapp.model.MessageType;
import lk.thuhina.water.whatsapp.model.WhatsAppMessage;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WhatsAppMessageRepository extends JpaRepository<WhatsAppMessage, Long> {

    /** Queued messages whose next attempt is due, oldest first. */
    @Query("select m from WhatsAppMessage m where m.status = lk.thuhina.water.whatsapp.model.MessageStatus.QUEUED "
            + "and (m.nextAttemptAt is null or m.nextAttemptAt <= :now) order by m.id")
    List<WhatsAppMessage> findDue(Instant now, Limit limit);

    Optional<WhatsAppMessage> findByMetaMessageId(String metaMessageId);

    /** A message of this type for the customer since {@code since} that was not skipped (once-a-week reminder rule). */
    boolean existsByCustomerIdAndTypeAndCreatedAtAfterAndStatusNot(long customerId, MessageType type, Instant since,
                                                                   MessageStatus status);

    /** A message of this type for the bill / quotation that was not skipped (e.g. "was the bill sent?" before "Bill cancelled"). */
    boolean existsByTypeAndSourceTypeAndSourceIdAndStatusNot(MessageType type, String sourceType, long sourceId, MessageStatus status);

    /** The latest message of this type for the bill / quotation (My Deliveries "WhatsApp bill sent"). */
    Optional<WhatsAppMessage> findFirstByTypeAndSourceTypeAndSourceIdOrderByIdDesc(MessageType type, String sourceType, long sourceId);
}
