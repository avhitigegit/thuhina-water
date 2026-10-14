package lk.thuhina.water.production.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import lk.thuhina.water.production.model.FactoryBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FactoryBatchRepository extends JpaRepository<FactoryBatch, Long> {

    List<FactoryBatch> findByDispatchIdOrderByIdAsc(long dispatchId);

    /** The batch row locked, so two returns of the same batch cannot both pass the "still at factory" check. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from FactoryBatch b where b.id = :id")
    Optional<FactoryBatch> lockById(@Param("id") long id);
}
