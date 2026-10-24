package lk.thuhina.water.masterdata.repository;

import java.util.List;

import lk.thuhina.water.masterdata.model.PriceEntry;
import org.springframework.data.repository.Repository;

/** Insert-only: no update or delete methods (a database trigger blocks them as well). */
public interface PriceEntryRepository extends Repository<PriceEntry, Long> {

    PriceEntry save(PriceEntry entry);

    /** The whole price history, newest effective date first. Small (a few rows per price per year). */
    List<PriceEntry> findAllByOrderByEffectiveFromDescIdDesc();

    List<PriceEntry> findByKindAndBottleTypeCodeOrderByEffectiveFromDesc(PriceEntry.Kind kind, String bottleTypeCode);
}
