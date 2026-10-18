package com.estatecrm.crm_service.service;

import com.estatecrm.crm_service.dto.CreateProjectRequest;
import com.estatecrm.crm_service.dto.ProjectResponse;
import com.estatecrm.crm_service.dto.UpdateProjectRequest;
import com.estatecrm.crm_service.entity.Project;
import com.estatecrm.crm_service.enums.ProjectStatus;
import com.estatecrm.crm_service.exception.ConflictException;
import com.estatecrm.crm_service.repository.ProductRepository;
import com.estatecrm.crm_service.repository.ProjectRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ProjectService {

    private static final String ADMIN = "ADMIN";
    private static final String MANAGER = "MANAGER";
    private static final String SALES = "SALES";

    private final ProjectRepository projectRepository;
    private final ProductRepository productRepository;
    private final ImageStorageService imageStorageService;
    private final ProjectAssignmentService projectAssignmentService;

    public ProjectService(
            ProjectRepository projectRepository,
            ProductRepository productRepository,
            ImageStorageService imageStorageService,
            ProjectAssignmentService projectAssignmentService) {
        this.projectRepository = projectRepository;
        this.productRepository = productRepository;
        this.imageStorageService = imageStorageService;
        this.projectAssignmentService = projectAssignmentService;
    }

    @Transactional
    public ProjectResponse create(CreateProjectRequest request, UUID actorId, String role) {
        requirePrivileged(role);
        Project project = new Project();
        apply(project, request.name(), request.location(), request.investor(),
                request.description(), request.imageUrl(), request.status());
        project.setCreatedBy(actorId);
        Project saved = projectRepository.save(project);
        projectAssignmentService.assignProjectCreator(saved, actorId, role);
        return ProjectResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<ProjectResponse> list(
            ProjectStatus status, String investor, String keyword, Pageable pageable) {
        return list(status, investor, keyword, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ProjectResponse> list(
            ProjectStatus status, String investor, String keyword, UUID actorId, String role, Pageable pageable) {
        java.util.List<UUID> allowedProjectIds = null;
        if ((MANAGER.equals(role) || SALES.equals(role)) && actorId != null) {
            allowedProjectIds = projectAssignmentService.getAssignedProjectIds(actorId);
            if (allowedProjectIds.isEmpty()) {
                return Page.empty(pageable);
            }
        }
        return projectRepository
                .findAll(ProjectRepository.filter(status, investor, keyword, allowedProjectIds), pageable)
                .map(ProjectResponse::from);
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(UUID projectId) {
        return get(projectId, null, null);
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(UUID projectId, UUID actorId, String role) {
        if ((MANAGER.equals(role) || SALES.equals(role)) && actorId != null && !projectAssignmentService.isAssignedToProject(actorId, projectId)) {
            throw new ResponseStatusException(FORBIDDEN, "Project is not assigned to you");
        }
        return ProjectResponse.from(find(projectId));
    }

    @Transactional
    public ProjectResponse update(
            UUID projectId, UpdateProjectRequest request, UUID actorId, String role) {
        requirePrivileged(role);
        if (MANAGER.equals(role) && actorId != null && !projectAssignmentService.isAssignedToProject(actorId, projectId)) {
            throw new ResponseStatusException(FORBIDDEN, "Project is not assigned to you");
        }
        Project project = find(projectId);
        apply(project, request.name(), request.location(), request.investor(),
                request.description(), request.imageUrl(), request.status());
        project.setUpdatedBy(actorId);
        return ProjectResponse.from(projectRepository.save(project));
    }

    @Transactional
    public void delete(UUID projectId, String role) {
        if (!ADMIN.equals(role)) {
            throw new ResponseStatusException(FORBIDDEN, "Only ADMIN can delete projects");
        }
        Project project = find(projectId);
        if (productRepository.existsByProjectId(projectId)) {
            throw new ConflictException("Project still has products; delete them first");
        }
        projectRepository.delete(project);
    }

    @Transactional
    public ProjectResponse uploadImage(UUID projectId, MultipartFile image, UUID actorId, String role) {
        requirePrivileged(role);
        if (MANAGER.equals(role) && actorId != null && !projectAssignmentService.isAssignedToProject(actorId, projectId)) {
            throw new ResponseStatusException(FORBIDDEN, "Project is not assigned to you");
        }
        Project project = find(projectId);
        project.setImageUrl(imageStorageService.upload("projects", projectId.toString(), image));
        project.setUpdatedBy(actorId);
        return ProjectResponse.from(projectRepository.save(project));
    }

    private void apply(
            Project project,
            String name,
            String location,
            String investor,
            String description,
            String imageUrl,
            ProjectStatus status) {
        if (name != null) {
            project.setName(name);
        }
        if (location != null) {
            project.setLocation(location);
        }
        if (investor != null) {
            project.setInvestor(investor);
        }
        if (description != null) {
            project.setDescription(description);
        }
        if (imageUrl != null) {
            project.setImageUrl(imageUrl);
        }
        if (status != null) {
            project.setStatus(status);
        }
    }

    private Project find(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Project not found"));
    }

    private void requirePrivileged(String role) {
        if (!ADMIN.equals(role) && !MANAGER.equals(role)) {
            throw new ResponseStatusException(FORBIDDEN, "Only ADMIN/MANAGER can manage projects");
        }
    }
}
