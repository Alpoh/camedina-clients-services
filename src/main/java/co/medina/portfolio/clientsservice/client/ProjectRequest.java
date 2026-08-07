package co.medina.portfolio.clientsservice.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProjectRequest(
        @NotBlank String name,
        @Size(max = 2000) String description,
        @NotNull ProjectStatus status) {
}
