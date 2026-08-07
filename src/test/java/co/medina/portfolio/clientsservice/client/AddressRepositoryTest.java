package co.medina.portfolio.clientsservice.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AddressRepositoryTest {

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Test
    void findByIdAndClientId_returnsEmpty_whenAddressBelongsToDifferentClient() {
        var owner = clientRepository.save(new Client("Jane Doe", "jane@example.com"));
        var otherClient = clientRepository.save(new Client("John Roe", "john@example.com"));
        var address = addressRepository.save(
                new Address(owner.getId(), "1 Main St", "Springfield", null, "12345", "US", AddressType.HOME, true));

        assertThat(addressRepository.findByIdAndClientId(address.getId(), otherClient.getId())).isEmpty();
        assertThat(addressRepository.findByIdAndClientId(address.getId(), owner.getId())).isPresent();
    }

    @Test
    void findByClientIdAndPrimaryTrue_returnsOnlyThePrimaryRow() {
        var owner = clientRepository.save(new Client("Jane Doe", "jane@example.com"));
        addressRepository.save(
                new Address(owner.getId(), "1 Main St", "Springfield", null, "12345", "US", AddressType.HOME, true));
        addressRepository.save(
                new Address(owner.getId(), "2 Elm St", "Springfield", null, "54321", "US", AddressType.BILLING, false));

        var primaries = addressRepository.findByClientIdAndPrimaryTrue(owner.getId());

        assertThat(primaries).hasSize(1);
        assertThat(primaries.getFirst().getStreet()).isEqualTo("1 Main St");
    }

    @Test
    void findByClientId_paginatesResults() {
        var owner = clientRepository.save(new Client("Jane Doe", "jane@example.com"));
        addressRepository.save(
                new Address(owner.getId(), "1 Main St", "Springfield", null, "12345", "US", AddressType.HOME, true));
        addressRepository.save(
                new Address(owner.getId(), "2 Elm St", "Springfield", null, "54321", "US", AddressType.BILLING, false));

        var page = addressRepository.findByClientId(owner.getId(), PageRequest.of(0, 1));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1);
    }
}
