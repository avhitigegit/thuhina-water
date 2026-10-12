package lk.thuhina.water.purchasing.repository;

import java.util.List;

import lk.thuhina.water.purchasing.model.SupplierPayment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, Long> {

    List<SupplierPayment> findAllByOrderByPayDateDescIdDesc();

    List<SupplierPayment> findBySupplierIdOrderByPayDateDescIdDesc(long supplierId);
}
