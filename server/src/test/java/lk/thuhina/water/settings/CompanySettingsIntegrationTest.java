package lk.thuhina.water.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import lk.thuhina.water.audit.repository.AuditLogRepository;
import lk.thuhina.water.security.Role;
import lk.thuhina.water.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

/** Company tab (M01 S5): details with validation, logo upload / show / remove. */
class CompanySettingsIntegrationTest extends IntegrationTest {

    /** Smallest valid PNG header (the type is checked from the content). */
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};

    @Autowired
    AuditLogRepository auditLog;

    @AfterEach
    void removeLogo() throws Exception {
        mvc.perform(delete("/settings/company/logo").cookie(login(createUser(Role.ADMIN))));
    }

    @Test
    void adminUploadsAPngLogoThatAnyLoggedInUserCanSee() throws Exception {
        Cookie admin = login(createUser(Role.ADMIN));
        MvcResult r = mvc.perform(multipart("/settings/company/logo")
                        .file(new MockMultipartFile("file", "logo.png", "image/png", PNG)).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Thuhina Water (Pvt) Ltd"))
                .andExpect(jsonPath("$.logo").value(startsWith("company/logo-")))
                .andExpect(jsonPath("$.logoUrl").value(startsWith("/settings/company/logo?v=")))
                .andReturn();
        assertThat((String) json.readValue(r.getResponse().getContentAsString(), Map.class).get("logo")).endsWith(".png");

        mvc.perform(get("/settings/company").cookie(admin))
                .andExpect(jsonPath("$.logoUrl").value(logoUrl(r)));

        Cookie accountant = login(createUser(Role.ACCOUNTANT));
        mvc.perform(get("/settings/company/logo").cookie(accountant))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Cache-Control", "max-age=86400, private"))
                .andExpect(content().bytes(PNG));
        mvc.perform(get("/settings/company/logo")).andExpect(status().isUnauthorized());

        assertThat(auditLog.findByEntityAndRefOrderByIdAsc("Setting", "company"))
                .anyMatch(a -> a.getDetails().equals("Company logo uploaded (PNG, 1 KB)"));
    }

    @Test
    void jpgIsAcceptedAndCanBeRemoved() throws Exception {
        Cookie admin = login(createUser(Role.ADMIN));
        mvc.perform(multipart("/settings/company/logo")
                        .file(new MockMultipartFile("file", "photo.jpeg", "image/jpeg", JPG)).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logo").value(org.hamcrest.Matchers.endsWith(".jpg")));
        mvc.perform(get("/settings/company/logo").cookie(admin))
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));

        mvc.perform(delete("/settings/company/logo").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logo").value(nullValue()))
                .andExpect(jsonPath("$.logoUrl").value(nullValue()));
        mvc.perform(get("/settings/company/logo").cookie(admin)).andExpect(status().isNotFound());
    }

    @Test
    void onlyPngOrJpgUpTo1Mb() throws Exception {
        Cookie admin = login(createUser(Role.ADMIN));
        // A text file named .png is refused – the content decides.
        mvc.perform(multipart("/settings/company/logo")
                        .file(new MockMultipartFile("file", "logo.png", "image/png", "<svg></svg>".getBytes())).cookie(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.file").value("Logo must be a PNG or JPG image."));

        byte[] big = Arrays.copyOf(PNG, 1024 * 1024 + 1);
        mvc.perform(multipart("/settings/company/logo")
                        .file(new MockMultipartFile("file", "big.png", "image/png", big)).cookie(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.file").value("Logo must be 1 MB or smaller."));

        mvc.perform(multipart("/settings/company/logo").cookie(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.file").value("Choose a file to upload."));
    }

    @Test
    void companyDetailsAreValidatedAndTheLogoKeyCannotBeSetByHand() throws Exception {
        Cookie admin = login(createUser(Role.ADMIN));
        mvc.perform(put("/settings/company").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", " ", "email", "not-an-email", "logo", "../../etc/passwd"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.name").value("Required."))
                .andExpect(jsonPath("$.details.fields.email").value("Enter a valid email address."))
                .andExpect(jsonPath("$.details.fields.logo").value("This setting cannot be changed here."));

        mvc.perform(put("/settings/company").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("phone", "011 285 4471", "email", ""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(""))
                .andExpect(jsonPath("$.logoUrl").value(nullValue()));
        // Put the seed value back for other tests.
        mvc.perform(put("/settings/company").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", "accounts@thuhinawater.lk")))).andExpect(status().isOk());
    }

    @Test
    void onlyAdminCanUploadOrRemove() throws Exception {
        for (Role role : new Role[] {Role.ACCOUNTANT, Role.DELIVERY_STAFF}) {
            Cookie cookie = login(createUser(role));
            mvc.perform(multipart("/settings/company/logo")
                            .file(new MockMultipartFile("file", "logo.png", "image/png", PNG)).cookie(cookie))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/settings/company/logo").cookie(cookie)).andExpect(status().isForbidden());
            mvc.perform(put("/settings/company").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                    .content(body(Map.of("name", "X")))).andExpect(status().isForbidden());
        }
    }

    private String logoUrl(MvcResult r) throws Exception {
        return (String) json.readValue(r.getResponse().getContentAsString(), Map.class).get("logoUrl");
    }
}
