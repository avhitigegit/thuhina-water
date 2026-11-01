package lk.thuhina.water.whatsapp.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lk.thuhina.water.security.Permissions;
import lk.thuhina.water.whatsapp.dto.WhatsAppDtos.QuotationSendRequest;
import lk.thuhina.water.whatsapp.dto.WhatsAppDtos.SendStatus;
import lk.thuhina.water.whatsapp.service.WhatsAppSendService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** WhatsApp from the screens that act (FR-61): My Deliveries (the bill just saved) and the customer quotation. */
@Tag(name = "WhatsApp")
@RestController
public class WhatsAppSendController {

    private static final String ENTRY = "hasAuthority('" + Permissions.DELIVERY_ENTRY + "')";
    private static final String QUOTATION_VIEW = "hasAuthority('" + Permissions.QUOTATIONS_VIEW + "')";
    private static final String QUOTATION_EDIT = "hasAuthority('" + Permissions.QUOTATIONS_EDIT + "')";

    private final WhatsAppSendService service;

    public WhatsAppSendController(WhatsAppSendService service) {
        this.service = service;
    }

    @Operation(summary = "WhatsApp of a bill I saved on the phone: sent / no WhatsApp (give a paper bill) / failed")
    @GetMapping("/delivery/mobile/bills/{id}/whatsapp")
    @PreAuthorize(ENTRY)
    public SendStatus billStatus(@PathVariable long id) {
        return service.billStatus(id);
    }

    @Operation(summary = "Send the WhatsApp bill again (the customer must have opted in with a valid number)")
    @PostMapping("/delivery/mobile/bills/{id}/whatsapp")
    @PreAuthorize(ENTRY)
    public SendStatus resendBill(@PathVariable long id) {
        return service.resendBill(id);
    }

    @Operation(summary = "WhatsApp of a quotation: last message and the number it would go to")
    @GetMapping("/customer-quotations/{id}/whatsapp")
    @PreAuthorize(QUOTATION_VIEW)
    public SendStatus quotationStatus(@PathVariable long id) {
        return service.quotationStatus(id);
    }

    @Operation(summary = "Send the quotation PDF on WhatsApp (a draft is marked as sent by WhatsApp); a new organisation must have "
            + "agreed (agreed = true)")
    @PostMapping("/customer-quotations/{id}/whatsapp")
    @PreAuthorize(QUOTATION_EDIT)
    public SendStatus sendQuotation(@PathVariable long id, @RequestBody(required = false) QuotationSendRequest request) {
        return service.sendQuotation(id, request);
    }
}
