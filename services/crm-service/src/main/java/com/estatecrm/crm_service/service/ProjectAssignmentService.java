package com.estatecrm.crm_service.service;

import com.estatecrm.crm_service.dto.AssignProjectMemberRequest;
import com.estatecrm.crm_service.dto.ProjectMemberResponse;
import com.estatecrm.crm_service.entity.Project;
import com.estatecrm.crm_service.entity.ProjectAssignment;
import com.estatecrm.crm_service.enums.ProjectAssignmentRole;
import com.estatecrm.crm_service.exception.ConflictException;
import com.estatecrm.crm_service.repository.ProjectAssignmentRepository;
import com.estatecrm.crm_service.repository.ProjectRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ProjectAssignmentService {

    private static final String ADMIN = "ADMIN";
    private static final String MANAGER = "MANAGER";

    private final ProjectAssignmentRepository assignmentRepository;
    private final ProjectRepository projectRepository;

    public ProjectAssignmentService(
            ProjectAssignmentRepository assignmentRepository,
            ProjectRepository projectRepository) {
        this.assignmentRepository = assignmentRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public ProjectMemberResponse assignMember(
            UUID projectId, AssignProjectMemberRequest request, UUID actorId, String role) {
        if (!ADMIN.equals(role)) {
            if (MANAGER.equals(role)) {
                boolean isProjectManager = assignmentRepository.existsByProjectIdAndUserIdAndAssignedRole(
                        projectId, actorId, ProjectAssignmentRole.MANAGER);
                if (!isProjectManager) {
                    throw new ResponseStatusException(FORBIDDEN, "Bạn không có quyền quản lý nhân sự của dự án này");
                }
                if (request.assignedRole() != ProjectAssignmentRole.SALES) {
                    throw new ResponseStatusException(FORBIDDEN, "Quản lý chỉ có quyền phân công nhân viên SALES vào dự án");
                }
            } else {
                throw new ResponseStatusException(FORBIDDEN, "Forbidden");
            }
        }
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Project not found"));

        if (assignmentRepository.existsByProjectIdAndUserId(projectId, request.userId())) {
            throw new ConflictException("User is already assigned to this project");
        }

        ProjectAssignment assignment = new ProjectAssignment(
                project, request.userId(), request.assignedRole(), actorId);
        return ProjectMemberResponse.from(assignmentRepository.save(assignment));
    }

    @Transactional
    public void assignProjectCreator(Project project, UUID creatorId, String role) {
        if (MANAGER.equals(role) && creatorId != null) {
            ProjectAssignment assignment = new ProjectAssignment(
                    project, creatorId, ProjectAssignmentRole.MANAGER, creatorId);
            assignmentRepository.save(assignment);
        }
    }

    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> getProjectMembers(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResponseStatusException(NOT_FOUND, "Project not found");
        }
        return assignmentRepository.findByProjectId(projectId).stream()
                .map(ProjectMemberResponse::from)
                .toList();
    }

    @Transactional
    public void removeMember(UUID projectId, UUID userId, UUID actorId, String role) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResponseStatusException(NOT_FOUND, "Project not found");
        }
        ProjectAssignment assignment = assignmentRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Assignment not found"));

        if (!ADMIN.equals(role)) {
            if (MANAGER.equals(role)) {
                boolean isProjectManager = assignmentRepository.existsByProjectIdAndUserIdAndAssignedRole(
                        projectId, actorId, ProjectAssignmentRole.MANAGER);
                if (!isProjectManager) {
                    throw new ResponseStatusException(FORBIDDEN, "Bạn không có quyền quản lý nhân sự của dự án này");
                }
                if (assignment.getAssignedRole() != ProjectAssignmentRole.SALES) {
                    throw new ResponseStatusException(FORBIDDEN, "Quản lý chỉ có quyền xóa nhân viên SALES khỏi dự án");
                }
            } else {
                throw new ResponseStatusException(FORBIDDEN, "Forbidden");
            }
        }
        assignmentRepository.deleteByProjectIdAndUserId(projectId, userId);
    }

    @Transactional
    public void removeMember(UUID projectId, UUID userId, String role) {
        removeMember(projectId, userId, null, role);
    }

    @Transactional(readOnly = true)
    public List<UUID> getAssignedProjectIds(UUID userId) {
        return assignmentRepository.findProjectIdsByUserId(userId);
    }

    @Transactional(readOnly = true)
    public boolean isAssignedToProject(UUID userId, UUID projectId) {
        return assignmentRepository.existsByProjectIdAndUserId(projectId, userId);
    }

    @Transactional(readOnly = true)
    public List<UUID> getManagedUserIds(UUID managerId) {
        if (managerId == null) {
            return List.of();
        }
        List<UUID> managedProjectIds = assignmentRepository.findManagedProjectIdsByUserId(managerId);
        if (managedProjectIds.isEmpty()) {
            return List.of(managerId);
        }
        java.util.Set<UUID> userIds = new java.util.HashSet<>(assignmentRepository.findUserIdsByProjectIdIn(managedProjectIds));
        userIds.add(managerId);
        return new java.util.ArrayList<>(userIds);
    }

    private void requireAdmin(String role) {
        if (!ADMIN.equals(role)) {
            throw new ResponseStatusException(FORBIDDEN, "Only ADMIN can assign members to projects");
        }
    }
}
