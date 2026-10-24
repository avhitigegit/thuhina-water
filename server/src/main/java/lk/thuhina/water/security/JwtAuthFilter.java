package lk.thuhina.water.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lk.thuhina.water.admin.model.AppUser;
import lk.thuhina.water.admin.repository.AppUserRepository;
import lk.thuhina.water.common.ErrorResponse;
import lk.thuhina.water.security.service.AuthCookies;
import lk.thuhina.water.security.service.JwtService;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads the login cookie on every request (design 8.1). The user is logged in only when the token is valid
 * <b>and</b> the user is still active <b>and</b> the token version matches – so deactivation or a password
 * reset logs the user out at once.
 * <p>
 * While {@code must_change_password} is set, only the "me", change-password and logout calls are allowed.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String PASSWORD_CHANGE_REQUIRED = "PASSWORD_CHANGE_REQUIRED";
    private static final Set<String> ALLOWED_BEFORE_PASSWORD_CHANGE = Set.of("/auth/me", "/auth/change-password", "/auth/logout");

    private final JwtService jwt;
    private final AuthCookies cookies;
    private final AppUserRepository users;
    private final ObjectMapper json;

    public JwtAuthFilter(JwtService jwt, AuthCookies cookies, AppUserRepository users, ObjectMapper json) {
        this.jwt = jwt;
        this.cookies = cookies;
        this.users = users;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        AuthUser user = cookies.read(request)
                .flatMap(jwt::parse)
                .flatMap(claims -> users.findById(claims.userId())
                        .filter(AppUser::isActive)
                        .filter(u -> u.getTokenVersion() == claims.tokenVersion()))
                .map(u -> new AuthUser(u.getId(), u.getUsername(), u.getFullName(), u.getRole(), u.isMustChangePassword()))
                .orElse(null);

        if (user != null) {
            if (user.mustChangePassword() && !ALLOWED_BEFORE_PASSWORD_CHANGE.contains(path(request))) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                json.writeValue(response.getWriter(),
                        ErrorResponse.of(PASSWORD_CHANGE_REQUIRED, "Please change your password first."));
                return;
            }
            List<GrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_" + user.role().name()));
            user.permissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(user, null, authorities));
        }
        chain.doFilter(request, response);
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
