package com.drini.wallet.auth.repository;

import com.drini.wallet.auth.domain.MagicLinkToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Reads, saves and uses up sign-in link tokens. */
public interface MagicLinkTokenRepository extends JpaRepository<MagicLinkToken, UUID> {

    Optional<MagicLinkToken> findByTokenHash(String tokenHash);

    /** Marks the token used only if it is still unused, so two simultaneous clicks cannot both sign in. */
    @Modifying
    @Transactional
    @Query("UPDATE MagicLinkToken t SET t.usedAt = :now WHERE t.id = :id AND t.usedAt IS NULL")
    int markUsed(@Param("id") UUID id, @Param("now") Instant now);
}
