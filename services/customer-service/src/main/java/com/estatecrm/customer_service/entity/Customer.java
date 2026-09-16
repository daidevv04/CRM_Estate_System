package com.estatecrm.customer_service.entity;

import com.estatecrm.customer_service.enums.CustomerStatus;
import com.estatecrm.customer_service.enums.DemandType;
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
 * Khach hang. owner_id cung cac cot audit la UUID tho tro toi user-db.users.id
 * vi bang users nam o project Supabase khac, khong the dat FK vat ly.
 * created_at/updated_at do DB sinh, xem ghi chu o hai cot ben duoi.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(length = 15)
    private String phone;

    @Column(length = 100)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "demand_type", nullable = false, length = 20)
    private DemandType demandType = DemandType.BUY;

    @Column(length = 50)
    private String source;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerStatus status = CustomerStatus.NEW;

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
