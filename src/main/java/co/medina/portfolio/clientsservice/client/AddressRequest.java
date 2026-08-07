package co.medina.portfolio.clientsservice.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record AddressRequest(
        @NotBlank String street,
        @NotBlank String city,
        String state,
        @NotBlank String postalCode,
        @NotBlank @Pattern(regexp = "^[A-Z]{2}$", message = "must be an ISO-3166-1 alpha-2 country code") String country,
        @NotNull AddressType type,
        boolean primary) {
}
