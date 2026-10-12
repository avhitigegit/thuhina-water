package lk.thuhina.water.delivery.controller;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lk.thuhina.water.delivery.dto.DeliveryDtos.DueList;
import lk.thuhina.water.delivery.dto.DeliveryDtos.Planning;
import lk.thuhina.water.delivery.dto.DeliveryDtos.Requirement;
import lk.thuhina.water.delivery.service.PlanningService;
import lk.thuhina.water.delivery.service.ScheduleService;
import lk.thuhina.water.security.Permissions;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Delivery schedule (design 7.2 Delivery). The due list feeds Enter Bills (M07) and the Daily Delivery List (M08, also for
 * Delivery Staff); planning and the bottle requirement are for the Admin only.
 */
@Tag(name = "Delivery")
@RestController
@RequestMapping("/delivery")
public class DeliveryController {

    private static final String DUE = "hasAnyAuthority('" + Permissions.SALES_EDIT + "', '" + Permissions.DELIVERY_PLANNING_VIEW + "', '"
            + Permissions.DELIVERY_LIST_VIEW + "')";

    private static final String PLANNING = "hasAuthority('" + Permissions.DELIVERY_PLANNING_VIEW + "')";

    private final ScheduleService schedule;
    private final PlanningService planning;

    public DeliveryController(ScheduleService schedule, PlanningService planning) {
        this.schedule = schedule;
        this.planning = planning;
    }

    @Operation(summary = "Customers due on a date (default today; for today also missed customers), with usual quantities, "
            + "bottles, prices, balance, bills already entered and counts for the info line")
    @GetMapping("/due")
    @PreAuthorize(DUE)
    public DueList due(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                       @RequestParam(required = false) Long area) {
        return schedule.dueList(date, area);
    }

    @Operation(summary = "Delivery Planning (FR-58): customers due on a date (default tomorrow) or all active customers, grouped by "
            + "route, with last / next / week-after deliveries, KPIs and the ready check per bottle type")
    @GetMapping("/planning")
    @PreAuthorize(PLANNING)
    public Planning planning(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                             @RequestParam(required = false) Long area,
                             @RequestParam(required = false, defaultValue = PlanningService.SHOW_DUE) String show) {
        return planning.planning(date, area, show);
    }

    @Operation(summary = "Upcoming bottle requirement (FR-45): filled bottles of one type (default the largest) needed per day "
            + "for the next days (default 14), against the filled stock in store and at the factory")
    @GetMapping("/requirement")
    @PreAuthorize(PLANNING)
    public Requirement requirement(@RequestParam(required = false) Integer days, @RequestParam(required = false) String bottle) {
        return planning.requirement(days, bottle);
    }
}
