package lk.thuhina.water.partners.repository;

import java.util.List;

import lk.thuhina.water.partners.model.Supplier;
import lk.thuhina.water.partners.model.SupplierItem.ItemType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    @EntityGraph(attributePaths = "items")
    List<Supplier> findAllByOrderByCodeAsc();

    /** Active suppliers of one item – used to tick the suppliers of a quotation request (M09). */
    @EntityGraph(attributePaths = "items")
    @Query("select s from Supplier s where s.active = true and exists (select 1 from SupplierItem i "
            + "where i.supplier = s and i.itemType = :type and i.itemCode = :code) order by s.code")
    List<Supplier> findActiveSupplying(@Param("type") ItemType type, @Param("code") String code);
}
