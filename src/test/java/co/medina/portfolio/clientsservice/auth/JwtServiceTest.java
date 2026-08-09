package co.medina.portfolio.clientsservice.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "test-only-jwt-signing-secret-at-least-32-bytes-long";

    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, Duration.ofHours(1)));

    @Test
    void extractEmail_returnsSubject_whenTokenValid() {
        var token = jwtService.generateToken("jane@example.com");

        assertThat(jwtService.extractEmail(token)).contains("jane@example.com");
    }

    @Test
    void extractEmail_returnsEmpty_whenTokenMalformed() {
        assertThat(jwtService.extractEmail("not-a-jwt")).isEmpty();
    }

    @Test
    void extractEmail_returnsEmpty_whenSignedWithDifferentSecret() {
        var otherService = new JwtService(
                new JwtProperties("a-completely-different-signing-secret-32-bytes+", Duration.ofHours(1)));
        var token = otherService.generateToken("jane@example.com");

        assertThat(jwtService.extractEmail(token)).isEmpty();
    }

    @Test
    void extractEmail_returnsEmpty_whenTokenExpired() {
        var expiredTokenService = new JwtService(new JwtProperties(SECRET, Duration.ofSeconds(-10)));
        var token = expiredTokenService.generateToken("jane@example.com");

        assertThat(jwtService.extractEmail(token)).isEmpty();
    }
}
