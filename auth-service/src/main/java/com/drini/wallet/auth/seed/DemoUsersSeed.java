package com.drini.wallet.auth.seed;

import com.drini.wallet.auth.domain.AccountType;
import com.drini.wallet.auth.domain.Identifier;
import com.drini.wallet.auth.domain.Role;
import com.drini.wallet.auth.domain.User;
import com.drini.wallet.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Creates the demo users at startup ("demo" profile): Elira (manager), Ana, Besi and Kafe Tirana (customers). */
@Component
@Profile("demo")
public class DemoUsersSeed implements ApplicationRunner {

    public static final UUID ELIRA_ID = UUID.fromString("00000000-0000-7000-8000-000000000001");
    public static final UUID ANA_ID = UUID.fromString("00000000-0000-7000-8000-000000000002");
    public static final UUID BESI_ID = UUID.fromString("00000000-0000-7000-8000-000000000003");
    public static final UUID KAFE_ID = UUID.fromString("00000000-0000-7000-8000-000000000004");

    private static final Logger log = LoggerFactory.getLogger(DemoUsersSeed.class);

    private final UserRepository users;

    public DemoUsersSeed(UserRepository users) {
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        createIfMissing(ELIRA_ID, "elira@wallet.local", "Elira Manager", Role.MANAGER, AccountType.PERSONAL);
        createIfMissing(ANA_ID, "ana@wallet.local", "Ana Customer", Role.CUSTOMER, AccountType.PERSONAL);
        createIfMissing(BESI_ID, "besi@wallet.local", "Besi Customer", Role.CUSTOMER, AccountType.PERSONAL);
        createIfMissing(KAFE_ID, "kafe@wallet.local", "Kafe Tirana", Role.CUSTOMER, AccountType.MERCHANT);
    }

    private void createIfMissing(UUID id, String email, String displayName, Role role, AccountType accountType) {
        if (users.existsById(id)) {
            return;
        }
        users.save(new User(id, Identifier.parse(email), displayName, role, accountType));
        log.info("Demo user created: {} ({})", displayName, role);
    }
}
