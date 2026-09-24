package com.estatecrm.crm_service.entity;

import com.estatecrm.crm_service.enums.LeadStage;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/** Ban ghi append-only cho funnel/report; khong sua hay xoa qua API. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "lead_stage_history")
public class LeadStageHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_stage", length = 20)
    private LeadStage fromStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_stage", nullable = false, length = 20)
    private LeadStage toStage;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Generated(event = EventType.INSERT)
    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;
}
