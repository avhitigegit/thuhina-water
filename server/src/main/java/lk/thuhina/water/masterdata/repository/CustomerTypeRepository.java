package lk.thuhina.water.masterdata.repository;

import java.util.List;

import lk.thuhina.water.masterdata.model.CustomerType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerTypeRepository extends JpaRepository<CustomerType, Long> {

    /** In the order they were added (Household, Shop, Office, Factory …), as the prototype's price grid. */
    List<CustomerType> findAllByOrderByIdAsc();

    List<CustomerType> findByActiveTrueOrderByIdAsc();

    boolean existsByNameIgnoreCase(String name);
}
