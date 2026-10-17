package com.estatecrm.crm_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "deal_payment_schedules")
public class DealPaymentSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "deal_id", nullable = false)
    private UUID dealId;

    @Column(name = "installment_no", nullable = false)
    private int installmentNo;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(precision = 5, scale = 2)
    private BigDecimal percentage;

    @Column(name = "amount_due", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountDue;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(columnDefinition = "text")
    private String notes;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
