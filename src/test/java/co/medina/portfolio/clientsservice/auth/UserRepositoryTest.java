package co.medina.portfolio.clientsservice.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void existsByEmail_returnsTrue_whenEmailAlreadyPersisted() {
        userRepository.save(new User("jane@example.com", "hashed-password"));

        assertThat(userRepository.existsByEmail("jane@example.com")).isTrue();
    }

    @Test
    void findByEmail_returnsEmpty_whenEmailNotPersisted() {
        assertThat(userRepository.findByEmail("nobody@example.com")).isEmpty();
    }
}
