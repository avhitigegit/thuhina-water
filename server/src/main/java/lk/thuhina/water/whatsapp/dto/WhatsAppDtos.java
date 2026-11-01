package lk.thuhina.water.whatsapp.dto;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Requests and responses of the WhatsApp tab (M13). */
public final class WhatsAppDtos {

    private WhatsAppDtos() {
    }

    /**
     * The settings as the WhatsApp tab shows them. Secrets are never returned – only whether one is saved.
     * {@code mode} LIVE = messages go to Meta; TEST = the test sender only writes them to the log.
     */
    public record SettingsView(
            boolean enabled,
            @Schema(nullable = true) String phoneNumberId,
            @Schema(nullable = true) String businessAccountId,
            boolean accessTokenSaved,
            boolean appSecretSaved,
            boolean verifyTokenSaved,
            boolean welcome,
            boolean quotation,
            boolean deliveryBill,
            boolean billCancelled,
            boolean paymentReceipt,
            boolean monthlyInvoice,
            boolean dueSoon,
            boolean overdue,
            @Schema(description = "Payment due soon: days before the due date") int daysBefore,
            @Schema(description = "Payment overdue: days after the due date") int daysAfter,
            @Schema(description = "Hour of the daily reminder run, 0 – 23") int reminderHour,
            String language,
            @Schema(description = "LIVE or TEST") String mode,
            String modeText,
            boolean encryptionReady,
            String webhookPath) {
    }

    /**
     * Changes to the settings; a field left out (null) stays as it is. Secrets and ids: text = save, empty text = remove.
     */
    public record SettingsRequest(
            Boolean enabled,
            String phoneNumberId,
            String businessAccountId,
            @Schema(description = "Write-only") String accessToken,
            @Schema(description = "Write-only") String appSecret,
            @Schema(description = "Write-only") String verifyToken,
            Boolean welcome,
            Boolean quotation,
            Boolean deliveryBill,
            Boolean billCancelled,
            Boolean paymentReceipt,
            Boolean monthlyInvoice,
            Boolean dueSoon,
            Boolean overdue,
            @Schema(description = "1 – 30") Integer daysBefore,
            @Schema(description = "1 – 30") Integer daysAfter,
            @Schema(description = "0 – 23") Integer reminderHour,
            @Schema(description = "Template language, e.g. en or en_US") String language) {
    }

    public record TestRequest(@NotBlank(message = "Enter the number to send the test message to.") String to) {
    }

    /** Result of "Test send" and "Verify connection". */
    public record CheckResult(boolean ok, String mode, String message) {
    }

    public record MessageRow(
            long id,
            Instant createdAt,
            @Schema(nullable = true) Long customerId,
            @Schema(nullable = true) String customerCode,
            @Schema(nullable = true) String customerName,
            String type,
            String typeLabel,
            String toNumber,
            String status,
            String statusLabel,
            boolean testMode,
            List<String> params,
            int attempts,
            @Schema(nullable = true) Instant sentAt,
            @Schema(nullable = true) Instant statusAt,
            @Schema(nullable = true) String error,
            @Schema(nullable = true) String sourceType,
            @Schema(nullable = true) Long sourceId) {
    }

    /** This month's counts for cost control (Meta charges per delivered template message; test mode is free). */
    public record MonthCount(String month, long sent, long failed, long skipped, long test) {
    }

    public record MessageListResponse(List<MessageRow> items, int page, int size, long total, MonthCount thisMonth) {
    }

    public record ReminderRunResult(int queued, int skipped, String message) {
    }

    /** One Meta template to create in WhatsApp Manager (category Utility) – the WhatsApp tab lists them for the setup. */
    public record TemplateRow(String type, String label, String name, String category, boolean document, String body,
                              List<String> params, boolean switchedOn) {
    }

    /**
     * WhatsApp of one bill / quotation for the screen that sent it: {@code state} NONE (nothing queued – see {@code text}),
     * QUEUED, SENT, DELIVERED, READ, FAILED or SKIPPED; {@code canSend} = WhatsApp and this message type are on and there is
     * a number to send to.
     */
    public record SendStatus(String state, String text, @Schema(nullable = true) String toNumber, boolean canSend,
                             @Schema(nullable = true) Long messageId) {
    }

    /** Send a quotation on WhatsApp: {@code to} default the customer's WhatsApp number or the quotation's phone. */
    public record QuotationSendRequest(String to, boolean agreed) {
    }
}
