package lk.thuhina.water.partners.repository;

import java.util.List;

import lk.thuhina.water.partners.model.Factory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FactoryRepository extends JpaRepository<Factory, Long> {

    List<Factory> findAllByOrderByCodeAsc();
}
