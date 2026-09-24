package com.estatecrm.user_service.repository;

import com.estatecrm.user_service.entity.OtpVerification;
import com.estatecrm.user_service.enums.OtpPurpose;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, UUID> {

    /** OTP moi nhat con hieu luc cua user cho mot purpose. */
    Optional<OtpVerification> findFirstByUserIdAndPurposeAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            UUID userId, OtpPurpose purpose, LocalDateTime now);

    /** Vo hieu OTP cu truoc khi phat hanh ma moi: moi thoi diem chi co 1 ma song. */
    @Modifying
    @Query("""
            update OtpVerification o set o.used = true
            where o.user.id = :userId and o.purpose = :purpose and o.used = false
            """)
    int invalidateUnused(@Param("userId") UUID userId, @Param("purpose") OtpPurpose purpose);
}
