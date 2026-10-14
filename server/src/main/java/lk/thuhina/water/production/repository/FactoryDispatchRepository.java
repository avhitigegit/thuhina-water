package lk.thuhina.water.production.repository;

import java.util.Optional;

import lk.thuhina.water.production.model.FactoryDispatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FactoryDispatchRepository extends JpaRepository<FactoryDispatch, Long> {

    Optional<FactoryDispatch> findByDispatchNo(String dispatchNo);
}
