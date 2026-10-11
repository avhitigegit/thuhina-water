package lk.thuhina.water.sales.repository;

import java.util.List;

import lk.thuhina.water.sales.model.SalesTxnLine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesTxnLineRepository extends JpaRepository<SalesTxnLine, Long> {

    List<SalesTxnLine> findByTxnIdOrderByLineNo(long txnId);

    List<SalesTxnLine> findByTxnIdInOrderByTxnIdAscLineNoAsc(List<Long> txnIds);
}
