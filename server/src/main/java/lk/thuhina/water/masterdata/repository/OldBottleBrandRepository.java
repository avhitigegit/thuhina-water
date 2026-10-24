package lk.thuhina.water.masterdata.repository;

import java.util.List;

import lk.thuhina.water.masterdata.model.OldBottleBrand;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OldBottleBrandRepository extends JpaRepository<OldBottleBrand, Long> {

    List<OldBottleBrand> findAllByOrderByActiveDescNameAsc();

    boolean existsByNameIgnoreCaseAndBottleTypeCode(String name, String bottleTypeCode);
}
