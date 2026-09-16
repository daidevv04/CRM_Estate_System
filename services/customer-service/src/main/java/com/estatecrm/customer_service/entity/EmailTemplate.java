package com.estatecrm.customer_service.entity;

import com.estatecrm.customer_service.enums.EmailTemplateCategory;
import com.estatecrm.customer_service.enums.EmailTemplateStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mau email tai su dung. subject/body la ban mau, cho phep giu placeholder
 * dang {{ten}} de dien luc gui.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "email_templates")
public class EmailTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EmailTemplateCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailTemplateStatus status = EmailTemplateStatus.ACTIVE;

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
