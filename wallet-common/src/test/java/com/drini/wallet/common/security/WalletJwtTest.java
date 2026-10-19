package com.drini.wallet.common.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checks the HS256 token round trip: a token signed with the shared secret is accepted and read correctly, a token
 * signed with another secret is refused.
 */
class WalletJwtTest {

    private static final String SECRET = "test-secret-that-is-at-least-32-characters-long";

    @Test
    void validTokenIsReadIntoCurrentUser() {
        UUID userId = UUID.randomUUID();
        String token = sign(SECRET, userId, "MANAGER");

        Jwt jwt = WalletJwt.decoder(SECRET).decode(token);
        CurrentUser user = CurrentUser.from(jwt);

        assertThat(user.getUserId()).isEqualTo(userId);
        assertThat(user.getRole()).isEqualTo("MANAGER");
        assertThat(user.getAccountType()).isEqualTo("PERSONAL");
        assertThat(user.getName()).isEqualTo("Elira");
        assertThat(user.isManager()).isTrue();
    }

    @Test
    void roleClaimBecomesSpringAuthority() {
        Jwt jwt = WalletJwt.decoder(SECRET).decode(sign(SECRET, UUID.randomUUID(), "CUSTOMER"));

        var authentication = WalletJwt.authenticationConverter().convert(jwt);

        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_CUSTOMER");
    }

    @Test
    void tokenWithAnotherSecretIsRefused() {
        String forged = sign("another-secret-that-is-also-32-characters-long", UUID.randomUUID(), "MANAGER");

        assertThatThrownBy(() -> WalletJwt.decoder(SECRET).decode(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void shortSecretIsRefused() {
        assertThatThrownBy(() -> WalletJwt.secretKey("short")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> WalletJwt.secretKey(null)).isInstanceOf(IllegalStateException.class);
    }

    private static String sign(String secret, UUID userId, String role) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(WalletJwt.secretKey(secret)));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim(WalletJwt.CLAIM_ROLE, role)
                .claim(WalletJwt.CLAIM_ACCOUNT_TYPE, "PERSONAL")
                .claim(WalletJwt.CLAIM_NAME, "Elira")
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
