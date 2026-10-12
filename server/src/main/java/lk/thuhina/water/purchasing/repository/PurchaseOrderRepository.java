package lk.thuhina.water.purchasing.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import lk.thuhina.water.purchasing.model.PoStatus;
import lk.thuhina.water.purchasing.model.PurchaseOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    @EntityGraph(attributePaths = "lines")
    List<PurchaseOrder> findAllByOrderByPoDateDescIdDesc();

    @EntityGraph(attributePaths = "lines")
    List<PurchaseOrder> findByStatusOrderByPoDateDescIdDesc(PoStatus status);

    List<PurchaseOrder> findByQrIdIn(Collection<Long> qrIds);

    Optional<PurchaseOrder> findFirstByQuotationIdAndStatusNot(long quotationId, PoStatus status);

    /** The PO row locked for a status change or a receipt. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PurchaseOrder p where p.id = :id")
    Optional<PurchaseOrder> lockById(@Param("id") long id);
}
