package lk.thuhina.water.customers.repository;

import java.util.List;

import lk.thuhina.water.customers.model.CustomerPrice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerPriceRepository extends JpaRepository<CustomerPrice, Long> {

    List<CustomerPrice> findByCustomerIdOrderByValidFromDescIdDesc(Long customerId);

    List<CustomerPrice> findByCustomerIdAndBottleTypeCodeOrderByValidFromAscIdAsc(Long customerId, String bottleTypeCode);

    List<CustomerPrice> findByCustomerIdIn(List<Long> customerIds);

    List<CustomerPrice> findAllByOrderByValidFromDescIdDesc();
}
