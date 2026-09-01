package co.medina.portfolio.clientsservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.medina.portfolio.clientsservice.common.ConflictException;
import co.medina.portfolio.clientsservice.common.NotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock
    private ClientRepository clientRepository;

    private ClientService clientService;

    @BeforeEach
    void setUp() {
        clientService = new ClientService(clientRepository);
    }

    @Test
    void create_throwsConflictException_whenEmailAlreadyExists() {
        var request = new ClientRequest("Jane Doe", "jane@example.com");
        when(clientRepository.existsByEmail("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> clientService.create(request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_savesClient_whenEmailAvailable() {
        var request = new ClientRequest("Jane Doe", "jane@example.com");
        when(clientRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var saved = clientService.create(request);

        assertThat(saved.getName()).isEqualTo("Jane Doe");
        assertThat(saved.getEmail()).isEqualTo("jane@example.com");
    }

    @Test
    void findById_throwsNotFoundException_whenIdUnknown() {
        var id = UUID.randomUUID();
        when(clientRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.findById(id))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findByEmail_throwsNotFoundException_whenEmailUnknown() {
        when(clientRepository.findByEmail("jane@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.findByEmail("jane@example.com"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void delete_throwsNotFoundException_whenIdUnknown() {
        var id = UUID.randomUUID();
        when(clientRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.delete(id))
                .isInstanceOf(NotFoundException.class);
        verify(clientRepository, never()).delete(any());
    }
}
