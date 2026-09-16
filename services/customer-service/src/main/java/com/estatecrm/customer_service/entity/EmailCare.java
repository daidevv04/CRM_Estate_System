package com.estatecrm.customer_service.entity;

import com.estatecrm.customer_service.enums.EmailCareStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

/**
 * Mot email da gui, luon gan voi mot nhat ky cham soc (customer_cares) de nam
 * trong dung dong thoi gian cua khach. subject/body duoc luu lai tai thoi diem
 * gui, khong lay dong tu template, de mau sua sau khong lam sai lich su.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "email_cares")
public class EmailCare {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_care_id", nullable = false)
    private CustomerCare customerCare;

    /** Mau da dung, null neu soan tay. Xoa mau khong xoa lich su gui. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private EmailTemplate template;

    @Column(name = "to_email", nullable = false, length = 100)
    private String toEmail;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailCareStatus status = EmailCareStatus.SENT;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    /*
     * Gia tri do DB sinh: DEFAULT CURRENT_TIMESTAMP khi insert, trigger
     * set_updated_at khi update. Khong set o Java: Java dung mui gio JVM con
     * DB dung mui gio cua phien lam viec, hai ben lech nhau.
     */
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;
}
