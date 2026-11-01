package lk.thuhina.water.whatsapp.controller;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.thuhina.water.security.Permissions;
import lk.thuhina.water.whatsapp.dto.WhatsAppDtos.CheckResult;
import lk.thuhina.water.whatsapp.dto.WhatsAppDtos.MessageListResponse;
import lk.thuhina.water.whatsapp.dto.WhatsAppDtos.ReminderRunResult;
import lk.thuhina.water.whatsapp.dto.WhatsAppDtos.SettingsRequest;
import lk.thuhina.water.whatsapp.dto.WhatsAppDtos.SettingsView;
import lk.thuhina.water.whatsapp.dto.WhatsAppDtos.TemplateRow;
import lk.thuhina.water.whatsapp.dto.WhatsAppDtos.TestRequest;
import lk.thuhina.water.whatsapp.model.MessageStatus;
import lk.thuhina.water.whatsapp.model.MessageType;
import lk.thuhina.water.whatsapp.service.PaymentReminderJob;
import lk.thuhina.water.whatsapp.service.WhatsAppOutbox;
import lk.thuhina.water.whatsapp.service.WhatsAppQueryService;
import lk.thuhina.water.whatsapp.service.WhatsAppSender;
import lk.thuhina.water.whatsapp.service.WhatsAppSettingsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Administration → WhatsApp tab (M13, design 9.2): settings, test send, connection check, reminders, message log. Admin only. */
@Tag(name = "WhatsApp")
@RestController
@RequestMapping("/whatsapp")
@PreAuthorize("hasAuthority('" + Permissions.ADMIN_MANAGE + "')")
public class WhatsAppController {

    private final WhatsAppSettingsService settings;
    private final WhatsAppSender sender;
    private final PaymentReminderJob reminders;
    private final WhatsAppQueryService query;

    public WhatsAppController(WhatsAppSettingsService settings, WhatsAppSender sender, PaymentReminderJob reminders,
                              WhatsAppQueryService query) {
        this.settings = settings;
        this.sender = sender;
        this.reminders = reminders;
        this.query = query;
    }

    @Operation(summary = "WhatsApp settings; secrets are never returned, only whether each one is saved")
    @GetMapping("/settings")
    public SettingsView settings() {
        return settings.view();
    }

    @Operation(summary = "Change WhatsApp settings (fields left out stay as they are; empty text removes a secret / id)")
    @PutMapping("/settings")
    public SettingsView update(@RequestBody SettingsRequest request) {
        return settings.update(request);
    }

    @Operation(summary = "Send Meta's hello_world test message to one number now (test mode: written to the log only)")
    @PostMapping("/test")
    public CheckResult test(@Valid @RequestBody TestRequest request) {
        return sender.test(request.to());
    }

    @Operation(summary = "Check the saved phone number ID and access token with Meta")
    @PostMapping("/verify")
    public CheckResult verify() {
        return sender.verify();
    }

    @Operation(summary = "Queue today's payment due soon / overdue messages now (the job also runs daily at the chosen hour)")
    @PostMapping("/reminders/run")
    public ReminderRunResult runReminders() {
        return reminders.run();
    }

    @Operation(summary = "Send the queued messages now instead of waiting for the next minute")
    @PostMapping("/send-now")
    public ReminderRunResult sendNow() {
        int n = sender.sendDue();
        return new ReminderRunResult(n, 0, n + " message(s) handled.");
    }

    @Operation(summary = "The Meta templates to create in WhatsApp Manager (name, category, header, body text, parameters)")
    @GetMapping("/templates")
    public List<TemplateRow> templates() {
        WhatsAppSettingsService.Config c = settings.config();
        return Arrays.stream(MessageType.values()).filter(t -> t != MessageType.TEST)
                .map(t -> new TemplateRow(t.name(), t.label(), t.template(), "UTILITY", t.document(), t.body(), t.params(),
                        WhatsAppOutbox.switchedOn(c, t)))
                .toList();
    }

    @Operation(summary = "Message log, newest first, with this month's counts")
    @GetMapping("/messages")
    public MessageListResponse messages(@RequestParam(required = false) MessageType type,
                                        @RequestParam(required = false) MessageStatus status,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                        @RequestParam(required = false) Long customer,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "50") int size) {
        return query.list(new WhatsAppQueryService.Filter(type, status, from, to, customer), page, size);
    }
}
