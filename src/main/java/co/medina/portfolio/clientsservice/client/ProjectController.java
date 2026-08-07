package co.medina.portfolio.clientsservice.client;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/clients/{clientId}/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    ResponseEntity<ProjectResponse> create(@PathVariable UUID clientId, @Valid @RequestBody ProjectRequest request) {
        var project = projectService.create(clientId, request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(project.getId())
                .toUri();
        return ResponseEntity.created(location).body(ProjectResponse.from(project));
    }

    @GetMapping("/{projectId}")
    ProjectResponse findById(@PathVariable UUID clientId, @PathVariable UUID projectId) {
        return ProjectResponse.from(projectService.findById(clientId, projectId));
    }

    @GetMapping
    Page<ProjectResponse> findAll(@PathVariable UUID clientId, @ParameterObject Pageable pageable) {
        return projectService.findAll(clientId, pageable).map(ProjectResponse::from);
    }

    @PutMapping("/{projectId}")
    ProjectResponse update(
            @PathVariable UUID clientId, @PathVariable UUID projectId, @Valid @RequestBody ProjectRequest request) {
        return ProjectResponse.from(projectService.update(clientId, projectId, request));
    }

    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID clientId, @PathVariable UUID projectId) {
        projectService.delete(clientId, projectId);
    }
}
