package lk.thuhina.water.quotations.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import lk.thuhina.water.quotations.model.CustomerQuotation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerQuotationRepository extends JpaRepository<CustomerQuotation, Long> {

    /** The quotation locked for a status change (two people answering at once). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from CustomerQuotation q where q.id = :id")
    Optional<CustomerQuotation> lockById(@Param("id") long id);
}
