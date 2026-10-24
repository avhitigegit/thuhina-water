package lk.thuhina.water.purchasing.repository;

import java.util.List;

import lk.thuhina.water.purchasing.model.GoodsReceipt;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Long> {

    @EntityGraph(attributePaths = "lines")
    List<GoodsReceipt> findByPoIdOrderByIdAsc(long poId);
}
