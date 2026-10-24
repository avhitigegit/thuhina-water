package lk.thuhina.water.numbering.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import lk.thuhina.water.numbering.model.DocSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocSequenceRepository extends JpaRepository<DocSequence, String> {

    /** {@code SELECT … FOR UPDATE}: other transactions wait until this one commits or rolls back. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DocSequence s where s.name = :name")
    Optional<DocSequence> lockByName(@Param("name") String name);
}
