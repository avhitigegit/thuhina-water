package lk.thuhina.water.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.http.Cookie;
import lk.thuhina.water.TestcontainersConfiguration;
import lk.thuhina.water.admin.model.AppUser;
import lk.thuhina.water.admin.repository.AppUserRepository;
import lk.thuhina.water.security.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/**
 * Base class for integration tests: the full application on a real PostgreSQL (Testcontainers), called through MockMvc.
 * All tests share one database, so each test creates its own users with unique usernames.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
public abstract class IntegrationTest {

    public static final String PASSWORD = "Secret-123";
    public static final String COOKIE = "TW_AUTH";
    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected AppUserRepository users;
    @Autowired
    protected PasswordEncoder encoder;
    @Autowired
    protected JdbcTemplate jdbc;

    protected AppUser createUser(Role role) {
        return createUser(role, false);
    }

    protected AppUser createUser(Role role, boolean mustChangePassword) {
        String username = role.name().toLowerCase().replace('_', '-') + "." + COUNTER.incrementAndGet() + "." + System.nanoTime() % 100000;
        return users.save(new AppUser(username, "Test " + role.label(), null, role, encoder.encode(PASSWORD), mustChangePassword));
    }

    protected MvcResult loginResult(String username, String password) throws Exception {
        return mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", password))))
                .andReturn();
    }

    /** Logs in and returns the login cookie to send with later requests. */
    protected Cookie login(AppUser user) throws Exception {
        MvcResult result = loginResult(user.getUsername(), PASSWORD);
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(200);
        Cookie cookie = result.getResponse().getCookie(COOKIE);
        assertThat(cookie).isNotNull();
        return cookie;
    }

    protected String body(Object value) {
        return json.writeValueAsString(value);
    }
}
