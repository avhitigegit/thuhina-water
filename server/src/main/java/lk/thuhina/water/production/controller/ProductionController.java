package lk.thuhina.water.production.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.thuhina.water.production.dto.ProductionDtos.BatchView;
import lk.thuhina.water.production.dto.ProductionDtos.DispatchRequest;
import lk.thuhina.water.production.dto.ProductionDtos.DispatchView;
import lk.thuhina.water.production.dto.ProductionDtos.FactoryMonth;
import lk.thuhina.water.production.dto.ProductionDtos.FactoryPaymentRequest;
import lk.thuhina.water.production.dto.ProductionDtos.FactoryPaymentRow;
import lk.thuhina.water.production.dto.ProductionDtos.ProductionSummary;
import lk.thuhina.water.production.dto.ProductionDtos.ReturnRequest;
import lk.thuhina.water.production.dto.ProductionDtos.ReturnResult;
import lk.thuhina.water.production.service.ProductionQueryService;
import lk.thuhina.water.production.service.ProductionService;
import lk.thuhina.water.security.Permissions;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Filling Factory (design 7.2 Production): dispatches, batches, returns, factory balances and payments. Admin full, Accountant view. */
@Tag(name = "Filling Factory")
@RestController
public class ProductionController {

    private static final String VIEW = "hasAuthority('" + Permissions.PRODUCTION_VIEW + "')";
    private static final String EDIT = "hasAuthority('" + Permissions.PRODUCTION_EDIT + "')";

    private final ProductionService service;
    private final ProductionQueryService query;

    public ProductionController(ProductionService service, ProductionQueryService query) {
        this.service = service;
        this.query = query;
    }

    @Operation(summary = "Page KPIs: empty and at factory per bottle type, bottles at and money owed to each factory")
    @GetMapping("/production/summary")
    @PreAuthorize(VIEW)
    public ProductionSummary summary() {
        return query.summary();
    }

    @Operation(summary = "Batches newest first with what came back; factory and open (still at the factory) optional")
    @GetMapping("/factory-batches")
    @PreAuthorize(VIEW)
    public List<BatchView> batches(@RequestParam(required = false) Long factory, @RequestParam(defaultValue = "false") boolean open) {
        return query.batches(factory, open);
    }

    @Operation(summary = "One batch with its returns")
    @GetMapping("/factory-batches/{id}")
    @PreAuthorize(VIEW)
    public BatchView batch(@PathVariable long id) {
        return query.batch(id);
    }

    @Operation(summary = "Send empties – one dispatch (FD-0001) for every bottle type, one batch (FB-0001) each, all-or-nothing")
    @PostMapping("/factory-dispatches")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EDIT)
    public DispatchView dispatch(@Valid @RequestBody DispatchRequest request) {
        return service.dispatch(request);
    }

    @Operation(summary = "A dispatch with its batches")
    @GetMapping("/factory-dispatches/{no}")
    @PreAuthorize(VIEW)
    public DispatchView dispatchByNo(@PathVariable String no) {
        return query.dispatch(no);
    }

    @Operation(summary = "Receive filled bottles (FR-0001) – partial returns allowed; rejects need a reason and are written off")
    @PostMapping("/factory-batches/{id}/returns")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EDIT)
    public ReturnResult receive(@PathVariable long id, @Valid @RequestBody ReturnRequest request) {
        return service.receive(id, request);
    }

    @Operation(summary = "Filling cost by factory and month, with paid, due and status")
    @GetMapping("/factory-balances")
    @PreAuthorize(VIEW)
    public List<FactoryMonth> balances() {
        return query.months();
    }

    @Operation(summary = "Factory payments, newest first")
    @GetMapping("/factory-payments")
    @PreAuthorize(VIEW)
    public List<FactoryPaymentRow> payments() {
        return query.payments();
    }

    @Operation(summary = "Record a factory payment (FP-0001) for a factory and month – reference required unless cash")
    @PostMapping("/factory-payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EDIT)
    public FactoryPaymentRow pay(@Valid @RequestBody FactoryPaymentRequest request) {
        return service.pay(request);
    }
}
