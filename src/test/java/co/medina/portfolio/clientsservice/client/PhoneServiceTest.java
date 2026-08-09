package co.medina.portfolio.clientsservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import co.medina.portfolio.clientsservice.common.NotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhoneServiceTest {

    @Mock
    private PhoneRepository phoneRepository;

    @Mock
    private ClientRepository clientRepository;

    private PhoneService phoneService;

    private final UUID clientId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        phoneService = new PhoneService(phoneRepository, clientRepository);
    }

    @Test
    void create_throwsNotFoundException_whenClientDoesNotExist() {
        when(clientRepository.existsById(clientId)).thenReturn(false);
        var request = new PhoneRequest("+1 555 0100", PhoneType.MOBILE, false);

        assertThatThrownBy(() -> phoneService.create(clientId, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_forcesPrimaryTrue_whenItIsTheClientsFirstPhone() {
        when(clientRepository.existsById(clientId)).thenReturn(true);
        when(phoneRepository.countByClientId(clientId)).thenReturn(0L);
        when(phoneRepository.findByClientIdAndPrimaryTrue(clientId)).thenReturn(List.of());
        when(phoneRepository.save(any(Phone.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var request = new PhoneRequest("+1 555 0100", PhoneType.MOBILE, false);

        var saved = phoneService.create(clientId, request);

        assertThat(saved.isPrimary()).isTrue();
    }

    @Test
    void create_demotesExistingPrimary_whenNewPhoneRequestsPrimary() {
        var existingPrimary = new Phone(clientId, "+1 555 0000", PhoneType.HOME, true);
        existingPrimary.setId(UUID.randomUUID());
        when(clientRepository.existsById(clientId)).thenReturn(true);
        when(phoneRepository.countByClientId(clientId)).thenReturn(1L);
        when(phoneRepository.findByClientIdAndPrimaryTrue(clientId)).thenReturn(List.of(existingPrimary));
        when(phoneRepository.save(any(Phone.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var request = new PhoneRequest("+1 555 0100", PhoneType.MOBILE, true);

        phoneService.create(clientId, request);

        assertThat(existingPrimary.isPrimary()).isFalse();
    }

    @Test
    void update_forcesPrimaryTrue_whenSoleRemainingPhone() {
        var phoneId = UUID.randomUUID();
        var phone = new Phone(clientId, "+1 555 0100", PhoneType.MOBILE, true);
        phone.setId(phoneId);
        when(clientRepository.existsById(clientId)).thenReturn(true);
        when(phoneRepository.findByIdAndClientId(phoneId, clientId)).thenReturn(Optional.of(phone));
        when(phoneRepository.countByClientId(clientId)).thenReturn(1L);
        when(phoneRepository.findByClientIdAndPrimaryTrue(clientId)).thenReturn(List.of(phone));
        when(phoneRepository.save(any(Phone.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var request = new PhoneRequest("+1 555 0100", PhoneType.MOBILE, false);

        var updated = phoneService.update(clientId, phoneId, request);

        assertThat(updated.isPrimary()).isTrue();
    }

    @Test
    void findById_throwsNotFoundException_whenPhoneBelongsToDifferentClient() {
        var phoneId = UUID.randomUUID();
        when(clientRepository.existsById(clientId)).thenReturn(true);
        when(phoneRepository.findByIdAndClientId(phoneId, clientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> phoneService.findById(clientId, phoneId))
                .isInstanceOf(NotFoundException.class);
    }
}
