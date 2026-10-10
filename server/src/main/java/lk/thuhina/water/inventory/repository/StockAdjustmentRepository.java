package lk.thuhina.water.inventory.repository;

import java.util.List;

import lk.thuhina.water.inventory.model.StockAdjustment;
import org.springframework.data.repository.Repository;

/** Insert-only: adjustments are never changed (a wrong count is corrected by another count). */
public interface StockAdjustmentRepository extends Repository<StockAdjustment, Long> {

    StockAdjustment save(StockAdjustment adjustment);

    List<StockAdjustment> findAllByOrderByAdjDateDescIdDesc();
}
