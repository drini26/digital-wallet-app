package com.drini.wallet.auth.repository;

import com.drini.wallet.auth.domain.Identifier;
import com.drini.wallet.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Reads and saves users. */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    default Optional<User> findByIdentifier(Identifier identifier) {
        if (identifier.isEmail()) {
            return findByEmail(identifier.getValue());
        }
        return findByPhone(identifier.getValue());
    }
}
