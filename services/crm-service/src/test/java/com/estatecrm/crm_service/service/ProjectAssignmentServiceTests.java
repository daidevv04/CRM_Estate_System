package com.estatecrm.crm_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.estatecrm.crm_service.dto.AssignProjectMemberRequest;
import com.estatecrm.crm_service.dto.ProjectMemberResponse;
import com.estatecrm.crm_service.entity.Project;
import com.estatecrm.crm_service.entity.ProjectAssignment;
import com.estatecrm.crm_service.enums.ProjectAssignmentRole;
import com.estatecrm.crm_service.exception.ConflictException;
import com.estatecrm.crm_service.repository.ProjectAssignmentRepository;
import com.estatecrm.crm_service.repository.ProjectRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ProjectAssignmentServiceTests {

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID MANAGER_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();

    @Mock
    private ProjectAssignmentRepository assignmentRepository;

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private ProjectAssignmentService assignmentService;

    @Test
    void adminCanAssignManagerToProject() {
        Project project = new Project();
        project.setId(PROJECT_ID);

        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(assignmentRepository.existsByProjectIdAndUserId(PROJECT_ID, MANAGER_ID)).thenReturn(false);
        when(assignmentRepository.save(any(ProjectAssignment.class))).thenAnswer(inv -> {
            ProjectAssignment pa = inv.getArgument(0);
            pa.setId(UUID.randomUUID());
            return pa;
        });

        ProjectMemberResponse res = assignmentService.assignMember(
                PROJECT_ID,
                new AssignProjectMemberRequest(MANAGER_ID, ProjectAssignmentRole.MANAGER),
                ADMIN_ID,
                "ADMIN");

        assertThat(res.projectId()).isEqualTo(PROJECT_ID);
        assertThat(res.userId()).isEqualTo(MANAGER_ID);
        assertThat(res.assignedRole()).isEqualTo(ProjectAssignmentRole.MANAGER);
    }

    @Test
    void managerCanAssignSalesToOwnProject() {
        Project project = new Project();
        project.setId(PROJECT_ID);

        UUID salesId = UUID.randomUUID();
        when(assignmentRepository.existsByProjectIdAndUserIdAndAssignedRole(PROJECT_ID, MANAGER_ID, ProjectAssignmentRole.MANAGER))
                .thenReturn(true);
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(assignmentRepository.existsByProjectIdAndUserId(PROJECT_ID, salesId)).thenReturn(false);
        when(assignmentRepository.save(any(ProjectAssignment.class))).thenAnswer(inv -> {
            ProjectAssignment pa = inv.getArgument(0);
            pa.setId(UUID.randomUUID());
            return pa;
        });

        ProjectMemberResponse res = assignmentService.assignMember(
                PROJECT_ID,
                new AssignProjectMemberRequest(salesId, ProjectAssignmentRole.SALES),
                MANAGER_ID,
                "MANAGER");

        assertThat(res.projectId()).isEqualTo(PROJECT_ID);
        assertThat(res.userId()).isEqualTo(salesId);
        assertThat(res.assignedRole()).isEqualTo(ProjectAssignmentRole.SALES);
    }

    @Test
    void managerCannotAssignManagerRole() {
        when(assignmentRepository.existsByProjectIdAndUserIdAndAssignedRole(PROJECT_ID, MANAGER_ID, ProjectAssignmentRole.MANAGER))
                .thenReturn(true);

        assertThatThrownBy(() -> assignmentService.assignMember(
                PROJECT_ID,
                new AssignProjectMemberRequest(UUID.randomUUID(), ProjectAssignmentRole.MANAGER),
                MANAGER_ID,
                "MANAGER"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Quản lý chỉ có quyền phân công nhân viên SALES");
    }

    @Test
    void managerCannotAssignToUnmanagedProject() {
        when(assignmentRepository.existsByProjectIdAndUserIdAndAssignedRole(PROJECT_ID, MANAGER_ID, ProjectAssignmentRole.MANAGER))
                .thenReturn(false);

        assertThatThrownBy(() -> assignmentService.assignMember(
                PROJECT_ID,
                new AssignProjectMemberRequest(UUID.randomUUID(), ProjectAssignmentRole.SALES),
                MANAGER_ID,
                "MANAGER"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Bạn không có quyền quản lý nhân sự");
    }

    @Test
    void salesRoleCannotAssignMembers() {
        assertThatThrownBy(() -> assignmentService.assignMember(
                PROJECT_ID,
                new AssignProjectMemberRequest(UUID.randomUUID(), ProjectAssignmentRole.SALES),
                UUID.randomUUID(),
                "SALES"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Forbidden");
    }

    @Test
    void rejectsDuplicateAssignment() {
        Project project = new Project();
        project.setId(PROJECT_ID);

        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(assignmentRepository.existsByProjectIdAndUserId(PROJECT_ID, MANAGER_ID)).thenReturn(true);

        assertThatThrownBy(() -> assignmentService.assignMember(
                PROJECT_ID,
                new AssignProjectMemberRequest(MANAGER_ID, ProjectAssignmentRole.MANAGER),
                ADMIN_ID,
                "ADMIN"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("User is already assigned");
    }

    @Test
    void adminCanRemoveMember() {
        ProjectAssignment pa = new ProjectAssignment();
        pa.setAssignedRole(ProjectAssignmentRole.MANAGER);

        when(projectRepository.existsById(PROJECT_ID)).thenReturn(true);
        when(assignmentRepository.findByProjectIdAndUserId(PROJECT_ID, MANAGER_ID)).thenReturn(Optional.of(pa));

        assignmentService.removeMember(PROJECT_ID, MANAGER_ID, ADMIN_ID, "ADMIN");

        verify(assignmentRepository).deleteByProjectIdAndUserId(PROJECT_ID, MANAGER_ID);
    }

    @Test
    void managerCanRemoveSalesFromOwnProject() {
        UUID salesId = UUID.randomUUID();
        ProjectAssignment salesPa = new ProjectAssignment();
        salesPa.setAssignedRole(ProjectAssignmentRole.SALES);

        when(projectRepository.existsById(PROJECT_ID)).thenReturn(true);
        when(assignmentRepository.findByProjectIdAndUserId(PROJECT_ID, salesId)).thenReturn(Optional.of(salesPa));
        when(assignmentRepository.existsByProjectIdAndUserIdAndAssignedRole(PROJECT_ID, MANAGER_ID, ProjectAssignmentRole.MANAGER))
                .thenReturn(true);

        assignmentService.removeMember(PROJECT_ID, salesId, MANAGER_ID, "MANAGER");

        verify(assignmentRepository).deleteByProjectIdAndUserId(PROJECT_ID, salesId);
    }

    @Test
    void managerCannotRemoveFellowManager() {
        UUID otherManagerId = UUID.randomUUID();
        ProjectAssignment managerPa = new ProjectAssignment();
        managerPa.setAssignedRole(ProjectAssignmentRole.MANAGER);

        when(projectRepository.existsById(PROJECT_ID)).thenReturn(true);
        when(assignmentRepository.findByProjectIdAndUserId(PROJECT_ID, otherManagerId)).thenReturn(Optional.of(managerPa));
        when(assignmentRepository.existsByProjectIdAndUserIdAndAssignedRole(PROJECT_ID, MANAGER_ID, ProjectAssignmentRole.MANAGER))
                .thenReturn(true);

        assertThatThrownBy(() -> assignmentService.removeMember(PROJECT_ID, otherManagerId, MANAGER_ID, "MANAGER"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Quản lý chỉ có quyền xóa nhân viên SALES");
    }

    @Test
    void returnsAssignedProjectIdsForUser() {
        when(assignmentRepository.findProjectIdsByUserId(MANAGER_ID)).thenReturn(List.of(PROJECT_ID));

        List<UUID> projectIds = assignmentService.getAssignedProjectIds(MANAGER_ID);

        assertThat(projectIds).containsExactly(PROJECT_ID);
    }

    @Test
    void getManagedUserIds_whenNoProjects_returnsOnlyManagerId() {
        when(assignmentRepository.findManagedProjectIdsByUserId(MANAGER_ID)).thenReturn(List.of());

        List<UUID> userIds = assignmentService.getManagedUserIds(MANAGER_ID);

        assertThat(userIds).containsExactly(MANAGER_ID);
    }

    @Test
    void getManagedUserIds_whenHasProjects_returnsManagerAndSales() {
        UUID salesId = UUID.randomUUID();
        when(assignmentRepository.findManagedProjectIdsByUserId(MANAGER_ID)).thenReturn(List.of(PROJECT_ID));
        when(assignmentRepository.findUserIdsByProjectIdIn(List.of(PROJECT_ID))).thenReturn(List.of(salesId, MANAGER_ID));

        List<UUID> userIds = assignmentService.getManagedUserIds(MANAGER_ID);

        assertThat(userIds).containsExactlyInAnyOrder(MANAGER_ID, salesId);
    }
}
