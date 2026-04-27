package com.n11.auth.repository;

import com.n11.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Mass-revoke every token in a family in one statement. Called when we
     * detect refresh-token reuse — the entire login session is compromised.
     */
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :now " +
           "WHERE r.familyId = :familyId AND r.revokedAt IS NULL")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    /**
     * Used at logout — revoke this single token only.
     */
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :now " +
           "WHERE r.id = :id AND r.revokedAt IS NULL")
    int revokeById(@Param("id") Long id, @Param("now") Instant now);

    /**
     * Best-effort cleanup: callable from a scheduled job to drop expired/revoked
     * rows. Not wired to a scheduler in the demo — DB stays small enough.
     */
    @Modifying
    @Query("DELETE FROM RefreshToken r " +
           "WHERE r.expiresAt < :cutoff OR r.revokedAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
