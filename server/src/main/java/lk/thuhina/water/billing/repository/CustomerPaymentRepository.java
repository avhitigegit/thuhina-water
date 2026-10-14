package lk.thuhina.water.billing.repository;

import lk.thuhina.water.billing.model.CustomerPayment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerPaymentRepository extends JpaRepository<CustomerPayment, Long> {
}
