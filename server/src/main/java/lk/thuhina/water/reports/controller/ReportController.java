package lk.thuhina.water.reports.controller;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.reports.dto.DashboardDtos.DashboardView;
import lk.thuhina.water.reports.dto.ReportDtos.ReportInfo;
import lk.thuhina.water.reports.dto.ReportDtos.ReportView;
import lk.thuhina.water.reports.service.DashboardService;
import lk.thuhina.water.reports.service.ReportExportService;
import lk.thuhina.water.reports.service.ReportService;
import lk.thuhina.water.security.Permissions;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard and reports (design 7.2 Reports). Dashboard: Admin and Accountant; reports: Admin all, Accountant five. */
@Tag(name = "Dashboard & reports")
@RestController
public class ReportController {

    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final DashboardService dashboard;
    private final ReportService reports;
    private final ReportExportService export;

    public ReportController(DashboardService dashboard, ReportService reports, ReportExportService export) {
        this.dashboard = dashboard;
        this.reports = reports;
        this.export = export;
    }

    @Operation(summary = "Dashboard: KPIs for today, low stock, today's deliveries, needs attention, sales of the last 14 days")
    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('" + Permissions.DASHBOARD_VIEW + "')")
    public DashboardView dashboard() {
        return dashboard.dashboard();
    }

    @Operation(summary = "The reports the user may run")
    @GetMapping("/reports")
    @PreAuthorize("hasAuthority('" + Permissions.REPORTS_VIEW + "')")
    public List<ReportInfo> list() {
        return reports.available();
    }

    @Operation(summary = "A report for a date range (default the start of last month to today; at most 366 days)")
    @GetMapping("/reports/{key}")
    @PreAuthorize("hasAuthority('" + Permissions.REPORTS_VIEW + "')")
    public ReportView report(@PathVariable String key,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.run(key, from, to);
    }

    @Operation(summary = "Export a report as PDF (format=pdf) or Excel (format=xlsx) – the same data as the screen")
    @GetMapping("/reports/{key}/export")
    @PreAuthorize("hasAuthority('" + Permissions.REPORTS_VIEW + "')")
    public ResponseEntity<byte[]> export(@PathVariable String key, @RequestParam(defaultValue = "pdf") String format,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        boolean pdf = "pdf".equalsIgnoreCase(format);
        if (!pdf && !"xlsx".equalsIgnoreCase(format)) {
            throw new ValidationException("format", "Choose PDF or Excel.");
        }
        ReportView r = reports.run(key, from, to);
        byte[] body = pdf ? export.pdf(r) : export.xlsx(r);
        ContentDisposition cd = (pdf ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(ReportExportService.filename(r, pdf ? "pdf" : "xlsx")).build();
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .contentType(pdf ? MediaType.APPLICATION_PDF : XLSX).body(body);
    }
}
