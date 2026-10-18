package com.estatecrm.crm_service.entity;

import com.estatecrm.crm_service.enums.CommissionMilestoneStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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
@Table(name = "deal_commission_milestones")
public class DealCommissionMilestone {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "deal_commission_id", nullable = false)
    private UUID dealCommissionId;

    @Column(name = "milestone_no", nullable = false)
    private int milestoneNo;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "customer_paid_threshold_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal customerPaidThresholdPct;

    @Column(name = "payout_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal payoutPercentage;

    @Column(name = "amount_expected", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountExpected;

    @Column(name = "amount_paid", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountPaid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CommissionMilestoneStatus status;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(columnDefinition = "text")
    private String note;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
