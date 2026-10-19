package com.drini.wallet.auth.service;

import com.drini.wallet.auth.api.dto.LoginResponse;
import com.drini.wallet.auth.config.AuthProperties;
import com.drini.wallet.auth.domain.User;
import com.drini.wallet.common.security.WalletJwt;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Creates the JWT a user gets after a successful login (HS256, signed with JWT_SECRET, valid 30 minutes). */
@Component
public class JwtIssuer {

    private final NimbusJwtEncoder encoder;
    private final AuthProperties properties;

    public JwtIssuer(AuthProperties properties) {
        this.properties = properties;
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(WalletJwt.secretKey(properties.getJwtSecret())));
    }

    public LoginResponse issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getJwtTtl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(WalletJwt.CLAIM_ROLE, user.getRole().name())
                .claim(WalletJwt.CLAIM_ACCOUNT_TYPE, user.getAccountType().name())
                .claim(WalletJwt.CLAIM_NAME, user.getDisplayName())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        LoginResponse response = new LoginResponse();
        response.setAccessToken(token);
        response.setExpiresAt(expiresAt);
        response.setUserId(user.getId());
        response.setDisplayName(user.getDisplayName());
        response.setRole(user.getRole().name());
        response.setAccountType(user.getAccountType().name());
        return response;
    }
}
