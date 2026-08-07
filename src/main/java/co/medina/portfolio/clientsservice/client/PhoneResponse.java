package co.medina.portfolio.clientsservice.client;

import java.time.Instant;
import java.util.UUID;

public record PhoneResponse(
        UUID id,
        UUID clientId,
        String number,
        PhoneType type,
        boolean primary,
        Instant createdAt,
        Instant updatedAt) {

    static PhoneResponse from(Phone phone) {
        return new PhoneResponse(
                phone.getId(),
                phone.getClientId(),
                phone.getNumber(),
                phone.getType(),
                phone.isPrimary(),
                phone.getCreatedAt(),
                phone.getUpdatedAt());
    }
}
