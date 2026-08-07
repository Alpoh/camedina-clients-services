package co.medina.portfolio.clientsservice.client;

import java.time.Instant;
import java.util.UUID;

public record ClientResponse(
        UUID id,
        String name,
        String email,
        Instant createdAt,
        Instant updatedAt) {

    static ClientResponse from(Client client) {
        return new ClientResponse(
                client.getId(),
                client.getName(),
                client.getEmail(),
                client.getCreatedAt(),
                client.getUpdatedAt());
    }
}
