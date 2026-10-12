package lk.thuhina.water.purchasing.repository;

import java.util.Collection;
import java.util.List;

import lk.thuhina.water.purchasing.model.Quotation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {

    @EntityGraph(attributePaths = "lines")
    List<Quotation> findByQrIdOrderByIdAsc(long qrId);

    @EntityGraph(attributePaths = "lines")
    List<Quotation> findByQrIdInOrderByIdAsc(Collection<Long> qrIds);
}
