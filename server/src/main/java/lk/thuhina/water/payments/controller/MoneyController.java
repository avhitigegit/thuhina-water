package lk.thuhina.water.payments.controller;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lk.thuhina.water.payments.dto.MoneyDtos.MoneyList;
import lk.thuhina.water.payments.service.MoneyQueryService;
import lk.thuhina.water.security.Permissions;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payments page (one home for money): Money in and Money out, newest first. Admin and Accountant; Delivery Staff none.
 * Saving a payment stays with its own endpoint (/payments, supplier payments, factory payments, expenses).
 */
@Tag(name = "Payments (money in and out)")
@RestController
@RequestMapping("/money")
public class MoneyController {

    private static final String VIEW = "hasAuthority('" + Permissions.BILLING_VIEW + "')";

    private final MoneyQueryService query;

    public MoneyController(MoneyQueryService query) {
        this.query = query;
    }

    @Operation(summary = "Money in: customer payments and money paid on bills (default this month)")
    @GetMapping("/in")
    @PreAuthorize(VIEW)
    public MoneyList moneyIn(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                             @RequestParam(required = false) String q) {
        return query.moneyIn(from, to, q);
    }

    @Operation(summary = "Money out: supplier payments, factory payments and expenses (default this month)")
    @GetMapping("/out")
    @PreAuthorize(VIEW)
    public MoneyList moneyOut(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                              @RequestParam(required = false) String q) {
        return query.moneyOut(from, to, q);
    }
}
