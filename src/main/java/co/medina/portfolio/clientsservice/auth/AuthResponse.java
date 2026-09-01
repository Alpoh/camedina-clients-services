package co.medina.portfolio.clientsservice.auth;

import java.util.UUID;

public record AuthResponse(String token, UUID id, Role role) {
}
