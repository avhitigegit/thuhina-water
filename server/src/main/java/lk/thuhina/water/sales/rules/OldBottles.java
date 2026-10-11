package lk.thuhina.water.sales.rules;

import lk.thuhina.water.sales.rules.SaleInputs.BottleInfo;
import lk.thuhina.water.sales.rules.SaleInputs.BrandInfo;

/** BR-02: old bottles handed in must be of an active brand accepted for that bottle type. */
final class OldBottles {

    private OldBottles() {
    }

    static void check(SaleCalc.Builder b, BottleInfo bottle, int oldBottles, BrandInfo brand) {
        if (oldBottles <= 0) {
            return;
        }
        if (brand == null || !brand.active()) {
            b.problem(Problem.field("oldBrandId", "Select an accepted old-bottle brand."));
        } else if (!brand.bottleTypeCode().equals(bottle.code())) {
            b.problem(Problem.field("oldBrandId", brand.name() + " bottles are accepted only for " + brand.bottleName() + "."));
        }
    }
}
