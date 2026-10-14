package lk.thuhina.water.billing.repository;

import lk.thuhina.water.billing.model.MonthlyInvoice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyInvoiceRepository extends JpaRepository<MonthlyInvoice, Long> {

    boolean existsByCustomerIdAndPeriod(long customerId, String period);
}
