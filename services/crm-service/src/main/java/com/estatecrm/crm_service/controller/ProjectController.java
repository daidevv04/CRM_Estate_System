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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
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
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return projectService.list(status, investor, keyword, pageable);
    }

    @GetMapping("/{projectId}")
    public ProjectResponse get(@PathVariable UUID projectId) {
        return projectService.get(projectId);
    }

    @PatchMapping("/{projectId}")
    public ProjectResponse update(
            @PathVariable UUID projectId,
            @Valid @RequestBody UpdateProjectRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return projectService.update(projectId, request, actorId(jwt), role(jwt));
    }

    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID projectId, @AuthenticationPrincipal Jwt jwt) {
        projectService.delete(projectId, role(jwt));
    }

    /** Lay id nguoi dang nhap tu claim subject cua JWT da verify. */
    private UUID actorId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    /** Lay vai tro tu claim "role" do user-service phat hanh. */
    private String role(Jwt jwt) {
        return jwt.getClaimAsString("role");
    }
}
