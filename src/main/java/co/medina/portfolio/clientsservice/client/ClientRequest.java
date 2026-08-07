package co.medina.portfolio.clientsservice.client;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ClientRequest(
        @NotBlank String name,
        @NotBlank @Email String email) {
}
