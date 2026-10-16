package com.estatecrm.crm_service.service;

import com.estatecrm.crm_service.dto.CreateProjectRequest;
import com.estatecrm.crm_service.dto.ProjectResponse;
import com.estatecrm.crm_service.dto.UpdateProjectRequest;
import com.estatecrm.crm_service.entity.Project;
import com.estatecrm.crm_service.enums.ProjectStatus;
import com.estatecrm.crm_service.repository.ProductRepository;
import com.estatecrm.crm_service.repository.ProjectRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTests {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ImageStorageService imageStorageService;
    @Mock
    private ProjectAssignmentService projectAssignmentService;

    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        projectService = new ProjectService(
                projectRepository,
                productRepository,
                imageStorageService,
                projectAssignmentService);
    }

    @Test
    @DisplayName("create project as manager auto-assigns creator to project")
    void create_whenManager_autoAssignsCreator() {
        UUID managerId = UUID.randomUUID();
        CreateProjectRequest req = new CreateProjectRequest(
                "Ocean Park", "Da Nang", "Sun Group", "Luxury", null, ProjectStatus.SELLING);

        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> {
            Project p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        ProjectResponse res = projectService.create(req, managerId, "MANAGER");
        assertThat(res).isNotNull();
        assertThat(res.name()).isEqualTo("Ocean Park");

        verify(projectAssignmentService).assignProjectCreator(any(Project.class), any(UUID.class), any(String.class));
    }

    @Test
    @DisplayName("list projects when manager has no assigned projects returns empty page")
    void list_whenManagerHasNoAssignedProjects_returnsEmpty() {
        UUID managerId = UUID.randomUUID();
        when(projectAssignmentService.getAssignedProjectIds(managerId)).thenReturn(List.of());

        Page<ProjectResponse> res = projectService.list(
                null, null, null, managerId, "MANAGER", PageRequest.of(0, 10));

        assertThat(res.getContent()).isEmpty();
    }

    @Test
    @DisplayName("get project fails when manager is not assigned to project")
    void get_whenManagerNotAssigned_throwsForbidden() {
        UUID projectId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();

        when(projectAssignmentService.isAssignedToProject(managerId, projectId)).thenReturn(false);

        assertThatThrownBy(() -> projectService.get(projectId, managerId, "MANAGER"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Project is not assigned to you");
    }

    @Test
    @DisplayName("update project fails when manager is not assigned to project")
    void update_whenManagerNotAssigned_throwsForbidden() {
        UUID projectId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();

        when(projectAssignmentService.isAssignedToProject(managerId, projectId)).thenReturn(false);

        UpdateProjectRequest req = new UpdateProjectRequest(
                "New Name", null, null, null, null, null);

        assertThatThrownBy(() -> projectService.update(projectId, req, managerId, "MANAGER"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Project is not assigned to you");
    }

    @Test
    @DisplayName("delete project fails when manager tries to delete")
    void delete_whenManager_throwsForbidden() {
        UUID projectId = UUID.randomUUID();
        assertThatThrownBy(() -> projectService.delete(projectId, "MANAGER"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only ADMIN can delete projects");
    }

    @Test
    @DisplayName("delete project succeeds when admin")
    void delete_whenAdmin_succeeds() {
        UUID projectId = UUID.randomUUID();
        Project project = new Project();
        project.setId(projectId);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(productRepository.existsByProjectId(projectId)).thenReturn(false);

        projectService.delete(projectId, "ADMIN");

        verify(projectRepository).delete(project);
    }
}
