package co.medina.portfolio.clientsservice.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PhoneRequest(
        @NotBlank String number,
        @NotNull PhoneType type,
        boolean primary) {
}
