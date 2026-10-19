package com.drini.wallet.auth.service;

import com.drini.wallet.auth.config.AuthProperties;
import com.drini.wallet.auth.domain.Identifier;
import com.drini.wallet.auth.domain.LinkPurpose;
import com.drini.wallet.auth.domain.MagicLinkToken;
import com.drini.wallet.auth.domain.User;
import com.drini.wallet.auth.notification.LinkSender;
import com.drini.wallet.auth.repository.MagicLinkTokenRepository;
import com.drini.wallet.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/** Creates single-use sign-in links and sends them. */
@Service
public class MagicLinkService {

    private static final Logger log = LoggerFactory.getLogger(MagicLinkService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final MagicLinkTokenRepository tokens;
    private final LinkSender linkSender;
    private final AuthProperties properties;

    public MagicLinkService(UserRepository users, MagicLinkTokenRepository tokens, LinkSender linkSender,
                            AuthProperties properties) {
        this.users = users;
        this.tokens = tokens;
        this.linkSender = linkSender;
        this.properties = properties;
    }

    /** Always answers the same way, so the endpoint does not reveal which addresses have an account. */
    public void requestSignInLink(String rawIdentifier) {
        Identifier identifier = Identifier.parse(rawIdentifier);
        Optional<User> user = users.findByIdentifier(identifier);
        if (user.isEmpty()) {
            log.info("Sign-in link requested for an unknown {}; nothing sent", identifier.isEmail() ? "email" : "phone");
            return;
        }
        sendLink(user.get(), identifier, LinkPurpose.SIGN_IN);
    }

    public void sendLink(User user, Identifier identifier, LinkPurpose purpose) {
        if (!user.isActive()) {
            log.info("No sign-in link for blocked user {}", user.getId());
            return;
        }

        // Only the SHA-256 hash is stored, so a copy of the database cannot be used to sign in.
        String rawToken = newRawToken();
        Instant expiresAt = Instant.now().plus(properties.getMagicLinkTtl());
        tokens.save(new MagicLinkToken(user.getId(), sha256Hex(rawToken), expiresAt));

        linkSender.send(identifier, rawToken, purpose);
    }

    static String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
