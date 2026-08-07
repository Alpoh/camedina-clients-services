package co.medina.portfolio.clientsservice.client;

import java.time.Instant;
import java.util.UUID;

public record AddressResponse(
        UUID id,
        UUID clientId,
        String street,
        String city,
        String state,
        String postalCode,
        String country,
        AddressType type,
        boolean primary,
        Instant createdAt,
        Instant updatedAt) {

    static AddressResponse from(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getClientId(),
                address.getStreet(),
                address.getCity(),
                address.getState(),
                address.getPostalCode(),
                address.getCountry(),
                address.getType(),
                address.isPrimary(),
                address.getCreatedAt(),
                address.getUpdatedAt());
    }
}
