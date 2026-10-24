package lk.thuhina.water.partners.repository;

import java.util.List;

import lk.thuhina.water.partners.model.FactoryCharge;
import org.springframework.data.repository.Repository;

/** Insert-only: no update or delete methods (a database trigger blocks them as well). */
public interface FactoryChargeRepository extends Repository<FactoryCharge, Long> {

    FactoryCharge save(FactoryCharge charge);

    /** A factory's charge history, newest first (latest date, then latest saved). */
    List<FactoryCharge> findByFactoryIdOrderByEffectiveFromDescIdDesc(Long factoryId);

    List<FactoryCharge> findAllByOrderByEffectiveFromDescIdDesc();
}
