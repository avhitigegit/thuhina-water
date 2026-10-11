package lk.thuhina.water.delivery.controller;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lk.thuhina.water.delivery.dto.DeliveryDtos.DueList;
import lk.thuhina.water.delivery.service.ScheduleService;
import lk.thuhina.water.security.Permissions;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Delivery schedule (design 7.2 Delivery). The due list feeds Enter Bills (M07) and the delivery lists (M08). */
@Tag(name = "Delivery")
@RestController
@RequestMapping("/delivery")
public class DeliveryController {

    private static final String DUE = "hasAnyAuthority('" + Permissions.SALES_EDIT + "', '" + Permissions.DELIVERY_PLANNING_VIEW + "', '"
            + Permissions.DELIVERY_LIST_VIEW + "')";

    private final ScheduleService schedule;

    public DeliveryController(ScheduleService schedule) {
        this.schedule = schedule;
    }

    @Operation(summary = "Customers due on a date (default today; for today also missed customers), with usual quantities, "
            + "bottles, prices, balance, bills already entered and counts for the info line")
    @GetMapping("/due")
    @PreAuthorize(DUE)
    public DueList due(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                       @RequestParam(required = false) Long area) {
        return schedule.dueList(date, area);
    }
}
