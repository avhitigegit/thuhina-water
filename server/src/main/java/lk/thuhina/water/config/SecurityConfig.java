package lk.thuhina.water.config;

import lk.thuhina.water.admin.repository.AppUserRepository;
import lk.thuhina.water.common.ErrorCodes;
import lk.thuhina.water.common.ErrorResponse;
import lk.thuhina.water.common.GlobalExceptionHandler;
import lk.thuhina.water.security.JwtAuthFilter;
import lk.thuhina.water.security.service.AuthCookies;
import lk.thuhina.water.security.service.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Security set-up (design 8): stateless, JWT in an httpOnly cookie, permissions checked on every endpoint
 * with {@code @PreAuthorize}. CSRF protection is off because the cookie is {@code SameSite=Strict} (the browser
 * never sends it from another site) and the API only accepts JSON from the same domain.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwt, AuthCookies cookies,
                                            AppUserRepository users, ObjectMapper json) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/login", "/auth/logout").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Only reachable when springdoc is switched on (local and UAT profiles).
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> writeError(res, json, HttpServletResponse.SC_UNAUTHORIZED,
                                ErrorResponse.of(ErrorCodes.UNAUTHORIZED, "Please log in.")))
                        .accessDeniedHandler((req, res, ex) -> writeError(res, json, HttpServletResponse.SC_FORBIDDEN,
                                ErrorResponse.of(ErrorCodes.FORBIDDEN, GlobalExceptionHandler.MSG_FORBIDDEN))))
                .addFilterBefore(new JwtAuthFilter(jwt, cookies, users, json), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** No form / basic login: stops Spring Boot from creating a default in-memory user. */
    @Bean
    UserDetailsService noUserDetailsService() {
        return username -> {
            throw new UsernameNotFoundException(username);
        };
    }

    private static void writeError(HttpServletResponse res, ObjectMapper json, int status, ErrorResponse body)
            throws java.io.IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        json.writeValue(res.getWriter(), body);
    }
}
