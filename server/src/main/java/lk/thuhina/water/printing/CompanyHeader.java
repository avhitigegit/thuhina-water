package lk.thuhina.water.printing;

import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Company details at the top of every print (Settings → Company) and the logo image, if one is uploaded. */
public record CompanyHeader(String name, String address, String phone, String email, String regNo,
                            @Schema(hidden = true) byte[] logo) {

    /** Lines under the name: address, "Tel … · email", "Reg. …" – empty parts are left out. */
    public List<String> lines() {
        List<String> out = new ArrayList<>();
        if (!blank(address)) {
            out.add(address);
        }
        String contact = String.join(" · ", List.of(blank(phone) ? "" : "Tel " + phone, blank(email) ? "" : email).stream()
                .filter(s -> !s.isEmpty()).toList());
        if (!contact.isEmpty()) {
            out.add(contact);
        }
        if (!blank(regNo)) {
            out.add("Reg. " + regNo);
        }
        return out;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
