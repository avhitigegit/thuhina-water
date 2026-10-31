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
            boolean leftAtDoor,
            boolean paymentReminder,
            boolean monthlyInvoice,
            int reminderDay,
            int reminderHour,
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
            Boolean leftAtDoor,
            Boolean paymentReminder,
            Boolean monthlyInvoice,
            @Schema(description = "1 = Monday … 7 = Sunday") Integer reminderDay,
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
}
