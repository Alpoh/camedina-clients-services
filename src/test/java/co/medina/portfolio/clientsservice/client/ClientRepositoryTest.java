package co.medina.portfolio.clientsservice.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ClientRepositoryTest {

    @Autowired
    private ClientRepository clientRepository;

    @Test
    void existsByEmail_returnsTrue_whenEmailAlreadyPersisted() {
        clientRepository.save(new Client("Jane Doe", "jane@example.com"));

        assertThat(clientRepository.existsByEmail("jane@example.com")).isTrue();
    }

    @Test
    void existsByEmail_returnsFalse_whenEmailNotPersisted() {
        assertThat(clientRepository.existsByEmail("nobody@example.com")).isFalse();
    }

    @Test
    void findByEmail_returnsClient_whenEmailPersisted() {
        clientRepository.save(new Client("Jane Doe", "jane@example.com"));

        assertThat(clientRepository.findByEmail("jane@example.com")).isPresent();
    }

    @Test
    void findByEmail_returnsEmpty_whenEmailNotPersisted() {
        assertThat(clientRepository.findByEmail("nobody@example.com")).isEmpty();
    }
}
