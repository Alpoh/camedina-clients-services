package co.medina.portfolio.clientsservice.client;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        UUID clientId,
        String name,
        String description,
        ProjectStatus status,
        Instant createdAt,
        Instant updatedAt) {

    static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getClientId(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                project.getCreatedAt(),
                project.getUpdatedAt());
    }
}
