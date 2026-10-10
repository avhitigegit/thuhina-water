package lk.thuhina.water.inventory.repository;

import lk.thuhina.water.inventory.model.MinStockLevel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MinStockLevelRepository extends JpaRepository<MinStockLevel, String> {
}
