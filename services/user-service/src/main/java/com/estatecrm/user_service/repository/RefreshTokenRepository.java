package com.estatecrm.user_service.repository;

import com.estatecrm.user_service.entity.RefreshToken;
import java.time.LocalDateTime;
import java.util.List;
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

    @Query("""
            select r from RefreshToken r
            where r.user.id = :userId and r.revokedAt is null and r.expiresAt > :now
            order by coalesce(r.lastUsedAt, r.createdAt) desc
            """)
    List<RefreshToken> findActiveByUserId(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("""
            update RefreshToken r set r.revokedAt = :now
            where r.id = :id and r.user.id = :userId and r.revokedAt is null
            """)
    int revokeByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("""
            update RefreshToken r set r.revokedAt = :now
            where r.user.id = :userId and r.id <> :sessionId and r.revokedAt is null
            """)
    int revokeAllExcept(@Param("userId") UUID userId, @Param("sessionId") UUID sessionId, @Param("now") LocalDateTime now);
}
