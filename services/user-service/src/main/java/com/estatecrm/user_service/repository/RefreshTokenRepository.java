package com.estatecrm.user_service.repository;

import com.estatecrm.user_service.entity.RefreshToken;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            update RefreshToken r set r.revokedAt = :now
            where r.tokenHash = :tokenHash and r.revokedAt is null and r.expiresAt > :now
            """)
    int tryRevoke(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);

    @Modifying
    @Query("""
            update RefreshToken r set r.revokedAt = :now
            where r.user.id = :userId and r.revokedAt is null
            """)
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") LocalDateTime now);
}
