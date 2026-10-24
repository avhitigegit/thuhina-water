package lk.thuhina.water.demo.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The demo data's Saturday dispatch (B-10): it may send only the empties that are in the store on that Saturday and stay
 * there on every later day, the way the Stock report counts "Empty in store" (balance now less the later days' changes).
 */
class DemoEmptiesRuleTest {

    @Test
    void emptiesArrivingTheNextDayAreNotSentOnSaturday() {
        // 12 empty now; Sunday's factory return brought 4 → only 8 were in the store on Saturday.
        assertThat(DemoDataService.lowestFrom(12, List.of(4))).isEqualTo(8);
    }

    @Test
    void theLowestLaterDayLimitsTheDispatch() {
        // Newest first: +5 on Tuesday, −6 on Monday (empties sent), +4 on Sunday → levels 7, 13, 9 back to Saturday.
        assertThat(DemoDataService.lowestFrom(12, List.of(5, -6, 4))).isEqualTo(7);
    }

    @Test
    void noLaterChangesSendsTheBalanceAndNoneAreSentWhenTheStoreWasShort() {
        assertThat(DemoDataService.lowestFrom(9, List.of())).isEqualTo(9);
        assertThat(DemoDataService.lowestFrom(3, List.of(5))).isEqualTo(-2); // nothing is sent (only > 0 goes out)
    }
}
