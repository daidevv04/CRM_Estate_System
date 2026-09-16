package com.estatecrm.crm_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Bon cot audit dung chung cho moi bang cua CRM.
 *
 * created_at/updated_at CHI do DB sinh: DEFAULT CURRENT_TIMESTAMP khi insert va
 * trigger set_updated_at khi update. Ca hai deu dung CURRENT_TIMESTAMP (gio cua
 * phien lam viec, tren Supabase la UTC) nen nhat quan voi nhau.
 *
 * Co tinh KHONG dat @PrePersist/@PreUpdate: neu Java cung ghi thi no ghi theo
 * timezone cua JVM, con trigger ghi theo timezone cua phien DB. Hai gia tri lech
 * nhau va trigger de len, khien updated_at nhay lui moi lan UPDATE.
 *
 * @Generated bao Hibernate dung ghi hai cot nay va doc lai gia tri DB vua sinh.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class AuditableEntity {

    /** DB sinh khi insert; khong bao gio duoc sua. */
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** DB sinh khi insert, va trigger ghi lai moi lan update. */
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;
}
