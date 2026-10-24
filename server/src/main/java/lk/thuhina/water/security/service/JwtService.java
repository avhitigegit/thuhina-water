package lk.thuhina.water.security.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import lk.thuhina.water.admin.model.AppUser;
import lk.thuhina.water.config.AppProperties;
import lk.thuhina.water.security.Role;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

/**
 * Issues and checks the login token (design 8.1): HS256, 12 hours, claims = user id (subject), role and
 * token version. The token alone is not trusted – {@code JwtAuthFilter} also checks the user is still active
 * and the token version still matches.
 */
@Service
public class JwtService {

    static final String ISSUER = "thuhina-water";
    static final String CLAIM_ROLE = "role";
    static final String CLAIM_TOKEN_VERSION = "tv";
    private static final int MIN_SECRET_BYTES = 32;

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final AppProperties.Jwt props;
    private final Clock clock;

    public JwtService(AppProperties properties, Clock clock) {
        this.props = properties.jwt();
        this.clock = clock;
        String secret = props == null ? null : props.secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("app.jwt.secret (JWT_SECRET) must be set and at least " + MIN_SECRET_BYTES + " characters long");
        }
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator();
        timestamps.setClock(clock);
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, new JwtIssuerValidator(ISSUER)));
        this.decoder = nimbus;
    }

    public String issue(AppUser user) {
        Instant now = Instant.now(clock);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(props.ttl()))
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_TOKEN_VERSION, user.getTokenVersion())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** The token's claims, or empty when it is malformed, wrongly signed or expired. */
    public Optional<TokenClaims> parse(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            Number version = jwt.getClaim(CLAIM_TOKEN_VERSION);
            return Optional.of(new TokenClaims(Long.valueOf(jwt.getSubject()),
                    Role.valueOf(jwt.getClaimAsString(CLAIM_ROLE)), version == null ? -1 : version.intValue()));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    public record TokenClaims(Long userId, Role role, int tokenVersion) {
    }
}
