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
class AddressServiceTest {

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private ClientRepository clientRepository;

    private AddressService addressService;

    private final UUID clientId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        addressService = new AddressService(addressRepository, clientRepository);
    }

    @Test
    void create_throwsNotFoundException_whenClientDoesNotExist() {
        when(clientRepository.existsById(clientId)).thenReturn(false);
        var request = new AddressRequest("1 Main St", "Springfield", null, "12345", "US", AddressType.HOME, false);

        assertThatThrownBy(() -> addressService.create(clientId, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_forcesPrimaryTrue_whenItIsTheClientsFirstAddress() {
        when(clientRepository.existsById(clientId)).thenReturn(true);
        when(addressRepository.countByClientId(clientId)).thenReturn(0L);
        when(addressRepository.findByClientIdAndPrimaryTrue(clientId)).thenReturn(List.of());
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var request = new AddressRequest("1 Main St", "Springfield", null, "12345", "US", AddressType.HOME, false);

        var saved = addressService.create(clientId, request);

        assertThat(saved.isPrimary()).isTrue();
    }

    @Test
    void create_demotesExistingPrimary_whenNewAddressRequestsPrimary() {
        var existingPrimary = new Address(clientId, "2 Elm St", "Springfield", null, "54321", "US", AddressType.HOME, true);
        existingPrimary.setId(UUID.randomUUID());
        when(clientRepository.existsById(clientId)).thenReturn(true);
        when(addressRepository.countByClientId(clientId)).thenReturn(1L);
        when(addressRepository.findByClientIdAndPrimaryTrue(clientId)).thenReturn(List.of(existingPrimary));
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var request = new AddressRequest("1 Main St", "Springfield", null, "12345", "US", AddressType.BILLING, true);

        addressService.create(clientId, request);

        assertThat(existingPrimary.isPrimary()).isFalse();
    }

    @Test
    void update_forcesPrimaryTrue_whenSoleRemainingAddress() {
        var addressId = UUID.randomUUID();
        var address = new Address(clientId, "1 Main St", "Springfield", null, "12345", "US", AddressType.HOME, true);
        address.setId(addressId);
        when(clientRepository.existsById(clientId)).thenReturn(true);
        when(addressRepository.findByIdAndClientId(addressId, clientId)).thenReturn(Optional.of(address));
        when(addressRepository.countByClientId(clientId)).thenReturn(1L);
        when(addressRepository.findByClientIdAndPrimaryTrue(clientId)).thenReturn(List.of(address));
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var request = new AddressRequest("1 Main St", "Springfield", null, "12345", "US", AddressType.HOME, false);

        var updated = addressService.update(clientId, addressId, request);

        assertThat(updated.isPrimary()).isTrue();
    }

    @Test
    void findById_throwsNotFoundException_whenAddressBelongsToDifferentClient() {
        var addressId = UUID.randomUUID();
        when(clientRepository.existsById(clientId)).thenReturn(true);
        when(addressRepository.findByIdAndClientId(addressId, clientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.findById(clientId, addressId))
                .isInstanceOf(NotFoundException.class);
    }
}
