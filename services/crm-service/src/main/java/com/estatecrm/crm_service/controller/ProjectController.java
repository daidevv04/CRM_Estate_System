package com.estatecrm.crm_service.controller;

import com.estatecrm.crm_service.dto.CreateProjectRequest;
import com.estatecrm.crm_service.dto.ProjectResponse;
import com.estatecrm.crm_service.dto.UpdateProjectRequest;
import com.estatecrm.crm_service.enums.ProjectStatus;
import com.estatecrm.crm_service.service.ProjectService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final com.estatecrm.crm_service.service.ProjectAssignmentService projectAssignmentService;

    public ProjectController(
            ProjectService projectService,
            com.estatecrm.crm_service.service.ProjectAssignmentService projectAssignmentService) {
        this.projectService = projectService;
        this.projectAssignmentService = projectAssignmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(
            @Valid @RequestBody CreateProjectRequest request, @AuthenticationPrincipal Jwt jwt) {
        return projectService.create(request, actorId(jwt), role(jwt));
    }

    @GetMapping
    public Page<ProjectResponse> list(
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) String investor,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        UUID actorId = jwt != null ? actorId(jwt) : null;
        String role = jwt != null ? role(jwt) : null;
        return projectService.list(status, investor, keyword, actorId, role, pageable);
    }

    @GetMapping("/my-assigned-ids")
    public java.util.List<UUID> getMyAssignedProjectIds(@AuthenticationPrincipal Jwt jwt) {
        return projectAssignmentService.getAssignedProjectIds(actorId(jwt));
    }

    @GetMapping("/my-managed-user-ids")
    public java.util.List<UUID> getMyManagedUserIds(@AuthenticationPrincipal Jwt jwt) {
        return projectAssignmentService.getManagedUserIds(actorId(jwt));
    }

    @GetMapping("/internal/managed-user-ids/{managerId}")
    public java.util.List<UUID> getManagedUserIds(@PathVariable UUID managerId) {
        return projectAssignmentService.getManagedUserIds(managerId);
    }

    @GetMapping("/{projectId}/members")
    public java.util.List<com.estatecrm.crm_service.dto.ProjectMemberResponse> getMembers(
            @PathVariable UUID projectId) {
        return projectAssignmentService.getProjectMembers(projectId);
    }

    @PostMapping("/{projectId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public com.estatecrm.crm_service.dto.ProjectMemberResponse assignMember(
            @PathVariable UUID projectId,
            @Valid @RequestBody com.estatecrm.crm_service.dto.AssignProjectMemberRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return projectAssignmentService.assignMember(projectId, request, actorId(jwt), role(jwt));
    }

    @DeleteMapping("/{projectId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            @PathVariable UUID projectId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal Jwt jwt) {
        projectAssignmentService.removeMember(projectId, userId, actorId(jwt), role(jwt));
    }

    @GetMapping("/{projectId}")
    public ProjectResponse get(@PathVariable UUID projectId, @AuthenticationPrincipal Jwt jwt) {
        UUID actorId = jwt != null ? actorId(jwt) : null;
        String role = jwt != null ? role(jwt) : null;
        return projectService.get(projectId, actorId, role);
    }

    @PatchMapping("/{projectId}")
    public ProjectResponse update(
            @PathVariable UUID projectId,
            @Valid @RequestBody UpdateProjectRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return projectService.update(projectId, request, actorId(jwt), role(jwt));
    }

    @PostMapping(value = "/{projectId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProjectResponse uploadImage(
            @PathVariable UUID projectId,
            @RequestPart("image") MultipartFile image,
            @AuthenticationPrincipal Jwt jwt) {
        return projectService.uploadImage(projectId, image, actorId(jwt), role(jwt));
    }

    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID projectId, @AuthenticationPrincipal Jwt jwt) {
        projectService.delete(projectId, role(jwt));
    }

    private UUID actorId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private String role(Jwt jwt) {
        return jwt.getClaimAsString("role");
    }
}
