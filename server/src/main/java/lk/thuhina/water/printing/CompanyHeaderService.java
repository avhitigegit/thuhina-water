package lk.thuhina.water.printing;

import lk.thuhina.water.settings.service.CompanyLogoService;
import lk.thuhina.water.settings.service.SettingsGroup;
import lk.thuhina.water.settings.service.SettingsService;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** The shared print header from the company settings and the uploaded logo. */
@Service
public class CompanyHeaderService {

    private final SettingsService settings;
    private final CompanyLogoService logo;

    public CompanyHeaderService(SettingsService settings, CompanyLogoService logo) {
        this.settings = settings;
        this.logo = logo;
    }

    public CompanyHeader current() {
        ObjectNode c = settings.get(SettingsGroup.COMPANY.key());
        return new CompanyHeader(text(c, "name"), text(c, "address"), text(c, "phone"), text(c, "email"), text(c, "regNo"),
                logo.current().map(CompanyLogoService.Logo::content).orElse(null));
    }

    private static String text(ObjectNode node, String key) {
        JsonNode v = node.get(key);
        return v == null || v.isNull() ? "" : v.asString();
    }
}
