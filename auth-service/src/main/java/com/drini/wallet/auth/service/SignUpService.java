package com.drini.wallet.auth.service;

import com.drini.wallet.auth.client.LedgerWalletClient;
import com.drini.wallet.auth.domain.AccountType;
import com.drini.wallet.auth.domain.Identifier;
import com.drini.wallet.auth.domain.LinkPurpose;
import com.drini.wallet.auth.domain.Role;
import com.drini.wallet.auth.domain.User;
import com.drini.wallet.auth.repository.UserRepository;
import com.drini.wallet.common.error.ErrorCode;
import com.drini.wallet.common.error.WalletException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/** Sign up: creates a CUSTOMER account and sends a welcome link. */
@Service
public class SignUpService {

    private static final Logger log = LoggerFactory.getLogger(SignUpService.class);

    private final UserRepository users;
    private final MagicLinkService magicLinks;
    private final LedgerWalletClient ledger;

    public SignUpService(UserRepository users, MagicLinkService magicLinks, LedgerWalletClient ledger) {
        this.users = users;
        this.magicLinks = magicLinks;
        this.ledger = ledger;
    }

    public void signUp(String rawDisplayName, String rawIdentifier, AccountType accountType) {
        Identifier identifier = Identifier.parse(rawIdentifier);
        String displayName = checkDisplayName(rawDisplayName);

        Optional<User> existing = users.findByIdentifier(identifier);
        if (existing.isPresent()) {
            magicLinks.sendLink(existing.get(), identifier, LinkPurpose.ALREADY_REGISTERED);
            return;
        }

        User user = new User(UUID.randomUUID(), identifier, displayName, Role.CUSTOMER, accountType);
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            log.info("Sign-up raced with another sign-up for the same address; nothing more to do");
            return;
        }

        ledger.createWallet(user.getId(), accountType.name(), displayName);

        magicLinks.sendLink(user, identifier, LinkPurpose.WELCOME);
    }

    static String checkDisplayName(String raw) {
        String name = raw == null ? "" : raw.strip();
        if (name.length() < 2 || name.length() > 100) {
            throw new WalletException(ErrorCode.VALIDATION_FAILED, "displayName must be 2 to 100 characters");
        }
        return name;
    }
}
