package lk.thuhina.water.customers.repository;

import java.util.List;
import java.util.Optional;

import lk.thuhina.water.customers.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByCode(String code);

    /** Customers whose phone has the same digits (spaces ignored) – the duplicate-phone warning. */
    @Query(value = "select * from customer where regexp_replace(phone, '[^0-9]', '', 'g') = :digits and id <> :exceptId "
            + "order by code limit 1", nativeQuery = true)
    Optional<Customer> findSamePhone(@Param("digits") String digits, @Param("exceptId") long exceptId);

    List<Customer> findByIdIn(List<Long> ids);
}
