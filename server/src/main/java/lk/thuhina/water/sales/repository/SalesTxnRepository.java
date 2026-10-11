package lk.thuhina.water.sales.repository;

import java.util.Optional;

import lk.thuhina.water.sales.model.SalesTxn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesTxnRepository extends JpaRepository<SalesTxn, Long> {

    /** BR-13: the bill that already uses this paper bill number (not a reversal, not reversed). */
    @Query("select t from SalesTxn t where upper(t.paperBillNo) = upper(:no) and t.kind <> lk.thuhina.water.sales.model.SalesKind.REVERSAL "
            + "and t.reversedById is null")
    Optional<SalesTxn> findActivePaperBill(@Param("no") String paperBillNo);
}
