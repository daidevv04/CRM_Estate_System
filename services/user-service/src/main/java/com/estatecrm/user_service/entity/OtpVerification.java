package com.estatecrm.user_service.entity;

import com.estatecrm.user_service.enums.OtpPurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "otp_verifications",
        indexes = @Index(
                name = "idx_otp_user_purpose_used_expires",
                columnList = "user_id, purpose, is_used, expires_at"))
public class OtpVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** SHA-256 hex cua OTP 6 chu so; database V2 khong luu plaintext OTP. */
    @Column(name = "otp_code", nullable = false, length = 64)
    private String otpCode;

    /** Dem lan nhap sai, huy OTP khi dat nguong de chong brute-force qua restart. */
    @Column(nullable = false)
    private int attempts;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OtpPurpose purpose;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "is_used", nullable = false)
    private boolean used;

    /*
     * Gia tri do DB sinh: DEFAULT CURRENT_TIMESTAMP khi insert, trigger
     * set_updated_at khi update. Ca hai dung gio cua phien lam viec cua DB.
     * Khong set o Java vi Java dung mui gio cua JVM, lech voi DB.
     */
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
