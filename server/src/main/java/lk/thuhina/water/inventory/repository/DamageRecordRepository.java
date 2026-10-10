package lk.thuhina.water.inventory.repository;

import java.util.List;

import lk.thuhina.water.inventory.model.DamageRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DamageRecordRepository extends JpaRepository<DamageRecord, Long> {

    List<DamageRecord> findAllByOrderByDamageDateDescIdDesc();
}
