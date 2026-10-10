package lk.thuhina.water.masterdata.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import lk.thuhina.water.masterdata.model.BottleType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BottleTypeRepository extends JpaRepository<BottleType, String> {

    /** Largest first (20L, 10L, 5L), as the prototype lists them. */
    List<BottleType> findAllByOrderByLitresDesc();

    List<BottleType> findByActiveTrueOrderByLitresDesc();

    Optional<BottleType> findByLitres(BigDecimal litres);
}
