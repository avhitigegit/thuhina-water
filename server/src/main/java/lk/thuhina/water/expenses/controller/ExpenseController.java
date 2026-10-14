package lk.thuhina.water.expenses.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.thuhina.water.expenses.dto.ExpenseDtos.DeleteExpenseRequest;
import lk.thuhina.water.expenses.dto.ExpenseDtos.ExpenseMonthView;
import lk.thuhina.water.expenses.dto.ExpenseDtos.ExpenseRequest;
import lk.thuhina.water.expenses.dto.ExpenseDtos.ExpenseRow;
import lk.thuhina.water.expenses.dto.ExpenseDtos.ExpenseSaved;
import lk.thuhina.water.expenses.dto.ExpenseDtos.ExpenseSummary;
import lk.thuhina.water.expenses.dto.ExpenseDtos.ExpenseTypeRequest;
import lk.thuhina.water.expenses.dto.ExpenseDtos.ExpenseTypeView;
import lk.thuhina.water.expenses.service.ExpenseQueryService;
import lk.thuhina.water.expenses.service.ExpenseService;
import lk.thuhina.water.security.Permissions;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Expenses (design 7.2 Expenses). Admin and Accountant full; Delivery Staff none. */
@Tag(name = "Expenses")
@RestController
public class ExpenseController {

    private static final String VIEW = "hasAuthority('" + Permissions.EXPENSES_VIEW + "')";
    private static final String EDIT = "hasAuthority('" + Permissions.EXPENSES_EDIT + "')";

    private final ExpenseService service;
    private final ExpenseQueryService query;

    public ExpenseController(ExpenseService service, ExpenseQueryService query) {
        this.service = service;
        this.query = query;
    }

    // ------------------------------------------------------------------ expenses

    @Operation(summary = "Monthly expenses: the entries for the month they are for (default this month), type and search optional; "
            + "month total, previous month and difference, totals by type, months to choose from")
    @GetMapping("/expenses")
    @PreAuthorize(VIEW)
    public ExpenseMonthView month(@RequestParam(required = false) String month, @RequestParam(required = false) Long type,
                                  @RequestParam(required = false) String q) {
        return query.month(month, type, q);
    }

    @Operation(summary = "Month by month: type × month from / to (YYYY-MM, default the 6 months up to this month), with totals")
    @GetMapping("/expenses/summary")
    @PreAuthorize(VIEW)
    public ExpenseSummary summary(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return query.summary(from, to);
    }

    @Operation(summary = "One expense")
    @GetMapping("/expenses/{id}")
    @PreAuthorize(VIEW)
    public ExpenseRow expense(@PathVariable long id) {
        return query.row(id);
    }

    @Operation(summary = "Add an expense (EX-0001) for the month it is for; reference required unless Cash")
    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EDIT)
    public ExpenseSaved create(@Valid @RequestBody ExpenseRequest request) {
        return service.create(request);
    }

    @Operation(summary = "Change any field of an expense")
    @PutMapping("/expenses/{id}")
    @PreAuthorize(EDIT)
    public ExpenseSaved update(@PathVariable long id, @Valid @RequestBody ExpenseRequest request) {
        return service.update(id, request);
    }

    @Operation(summary = "Delete an expense – the reason is required and kept in the audit log (soft delete)")
    @DeleteMapping("/expenses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(EDIT)
    public void delete(@PathVariable long id, @Valid @RequestBody DeleteExpenseRequest request) {
        service.delete(id, request.reason(), request.version());
    }

    // ------------------------------------------------------------------ types

    @Operation(summary = "Expense types (active and inactive)")
    @GetMapping("/expense-types")
    @PreAuthorize(VIEW)
    public List<ExpenseTypeView> types() {
        return query.types();
    }

    @Operation(summary = "Add an expense type")
    @PostMapping("/expense-types")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EDIT)
    public ExpenseTypeView createType(@Valid @RequestBody ExpenseTypeRequest request) {
        return service.createType(request);
    }

    @Operation(summary = "Rename an expense type, change its description, or activate / deactivate it")
    @PutMapping("/expense-types/{id}")
    @PreAuthorize(EDIT)
    public ExpenseTypeView updateType(@PathVariable long id, @Valid @RequestBody ExpenseTypeRequest request) {
        return service.updateType(id, request);
    }
}
