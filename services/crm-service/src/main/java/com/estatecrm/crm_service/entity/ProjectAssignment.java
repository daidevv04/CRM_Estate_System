package com.estatecrm.crm_service.entity;

import com.estatecrm.crm_service.enums.ProjectAssignmentRole;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "project_assignments")
public class ProjectAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assigned_role", nullable = false, length = 20)
    private ProjectAssignmentRole assignedRole;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt = LocalDateTime.now();

    @Column(name = "assigned_by")
    private UUID assignedBy;

    public ProjectAssignment(Project project, UUID userId, ProjectAssignmentRole assignedRole, UUID assignedBy) {
        this.project = project;
        this.userId = userId;
        this.assignedRole = assignedRole;
        this.assignedBy = assignedBy;
        this.assignedAt = LocalDateTime.now();
    }
}
