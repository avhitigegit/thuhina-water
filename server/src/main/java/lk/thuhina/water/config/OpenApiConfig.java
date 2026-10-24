package lk.thuhina.water.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI description. Swagger UI is switched on only in the local and UAT profiles
 * ({@code springdoc.*.enabled}); the client generates its API types from {@code /api/v3/api-docs}.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI thuhinaOpenApi(@Value("${app.cookie.name:TW_AUTH}") String cookieName) {
        return new OpenAPI()
                .info(new Info().title("Thuhina Water API").version("v1")
                        .description("Sales, inventory and distribution system. Log in with POST /auth/login; "
                                + "the session is kept in an httpOnly cookie."))
                .components(new Components().addSecuritySchemes("cookieAuth",
                        new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name(cookieName)))
                .addSecurityItem(new SecurityRequirement().addList("cookieAuth"));
    }
}
