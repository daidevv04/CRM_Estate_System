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
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Du an bat dong san. Doc: moi vai tro. Tao/sua/xoa: ADMIN/MANAGER (giong
 * EmailTemplateService) vi du lieu catalog duoc chia keo ca he thong.
 */
@Service
public class ProjectService {

    private static final String ADMIN = "ADMIN";
    private static final String MANAGER = "MANAGER";

    private final ProjectRepository projectRepository;
    private final ProductRepository productRepository;

    public ProjectService(ProjectRepository projectRepository, ProductRepository productRepository) {
        this.projectRepository = projectRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public ProjectResponse create(CreateProjectRequest request, UUID actorId, String role) {
        requirePrivileged(role);
        Project project = new Project();
        apply(project, request.name(), request.location(), request.investor(),
                request.description(), request.status());
        project.setCreatedBy(actorId);
        return ProjectResponse.from(projectRepository.save(project));
    }

    @Transactional(readOnly = true)
    public Page<ProjectResponse> list(
            ProjectStatus status, String investor, String keyword, Pageable pageable) {
        return projectRepository
                .findAll(ProjectRepository.filter(status, investor, keyword), pageable)
                .map(ProjectResponse::from);
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(UUID projectId) {
        return ProjectResponse.from(find(projectId));
    }

    @Transactional
    public ProjectResponse update(
            UUID projectId, UpdateProjectRequest request, UUID actorId, String role) {
        requirePrivileged(role);
        Project project = find(projectId);
        apply(project, request.name(), request.location(), request.investor(),
                request.description(), request.status());
        project.setUpdatedBy(actorId);
        return ProjectResponse.from(projectRepository.save(project));
    }

    /**
     * Xoa du an. Chan truoc neu con san pham (FK khong cascade), de tra message
     * dung ngu canh thay vi loi DB.
     */
    @Transactional
    public void delete(UUID projectId, String role) {
        requirePrivileged(role);
        Project project = find(projectId);
        if (productRepository.existsByProjectId(projectId)) {
            throw new ConflictException("Project still has products; delete them first");
        }
        projectRepository.delete(project);
    }

    /** Chi ghi vao field nao request gui den; null = giu nguyen. */
    private void apply(
            Project project,
            String name,
            String location,
            String investor,
            String description,
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
