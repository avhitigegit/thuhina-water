package lk.thuhina.water.purchasing.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import lk.thuhina.water.purchasing.model.QrStatus;
import lk.thuhina.water.purchasing.model.QuotationRequest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface QuotationRequestRepository extends JpaRepository<QuotationRequest, Long> {

    /** Newest first, with the items (the suppliers asked are batch-loaded, see {@link QuotationRequest}). */
    @EntityGraph(attributePaths = "items")
    List<QuotationRequest> findAllByOrderByRequestDateDescIdDesc();

    @EntityGraph(attributePaths = "items")
    List<QuotationRequest> findByStatusOrderByRequestDateDescIdDesc(QrStatus status);

    /** The request row locked, so two users cannot record or choose at the same moment. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from QuotationRequest q where q.id = :id")
    Optional<QuotationRequest> lockById(@Param("id") long id);
}
