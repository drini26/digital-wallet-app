package com.drini.wallet.auth.service;

import com.drini.wallet.auth.api.dto.LoginResponse;
import com.drini.wallet.auth.domain.Identifier;
import com.drini.wallet.auth.domain.MagicLinkToken;
import com.drini.wallet.auth.domain.User;
import com.drini.wallet.auth.repository.MagicLinkTokenRepository;
import com.drini.wallet.auth.repository.UserRepository;
import com.drini.wallet.common.error.ErrorCode;
import com.drini.wallet.common.error.WalletException;
import org.springframework.stereotype.Service;

import java.time.Instant;

/** Login with a magic link: checks the token and returns a JWT. */
@Service
public class LoginService {

    private final MagicLinkTokenRepository tokens;
    private final UserRepository users;
    private final JwtIssuer jwtIssuer;

    public LoginService(MagicLinkTokenRepository tokens, UserRepository users, JwtIssuer jwtIssuer) {
        this.tokens = tokens;
        this.users = users;
        this.jwtIssuer = jwtIssuer;
    }

    public LoginResponse login(String rawIdentifier, String rawToken) {
        Identifier identifier = Identifier.parse(rawIdentifier);

        MagicLinkToken token = tokens.findByTokenHash(MagicLinkService.sha256Hex(rawToken))
                .orElseThrow(() -> invalidLink());

        User user = users.findById(token.getUserId()).orElseThrow(() -> invalidLink());
        if (!user.owns(identifier) || !user.isActive()) {
            throw invalidLink();
        }

        Instant now = Instant.now();
        if (token.isExpired(now)) {
            throw new WalletException(ErrorCode.MAGIC_LINK_EXPIRED, "This sign-in link has expired. Ask for a new one.");
        }

        // Conditional update: only one of two simultaneous clicks gets 1.
        if (token.isUsed() || tokens.markUsed(token.getId(), now) == 0) {
            throw new WalletException(ErrorCode.MAGIC_LINK_USED, "This sign-in link was already used. Ask for a new one.");
        }

        return jwtIssuer.issue(user);
    }

    private static WalletException invalidLink() {
        return new WalletException(ErrorCode.MAGIC_LINK_INVALID, "This sign-in link is not valid");
    }
}
