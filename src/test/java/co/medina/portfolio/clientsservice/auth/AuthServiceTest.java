package co.medina.portfolio.clientsservice.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import co.medina.portfolio.clientsservice.common.ConflictException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, authenticationManager, jwtService);
    }

    @Test
    void register_throwsConflictException_whenEmailAlreadyExists() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(true);
        var request = new RegisterRequest("jane@example.com", "password123");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void register_savesUserWithEncodedPasswordAndClientRole_andReturnsAuthResponse() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateToken("jane@example.com")).thenReturn("a-jwt");
        var request = new RegisterRequest("jane@example.com", "password123");

        var response = authService.register(request);

        assertThat(response.token()).isEqualTo("a-jwt");
        assertThat(response.role()).isEqualTo(Role.CLIENT);
    }

    @Test
    void login_returnsAuthResponse_whenCredentialsValid() {
        var userId = UUID.randomUUID();
        var user = new User("jane@example.com", "encoded-password", Role.ADMIN);
        user.setId(userId);
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken("jane@example.com")).thenReturn("a-jwt");
        var request = new LoginRequest("jane@example.com", "password123");

        var response = authService.login(request);

        assertThat(response.token()).isEqualTo("a-jwt");
        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void login_throwsAuthenticationException_whenCredentialsInvalid() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
        var request = new LoginRequest("jane@example.com", "wrong-password");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }
}
