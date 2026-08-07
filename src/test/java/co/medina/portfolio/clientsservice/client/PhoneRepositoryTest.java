package co.medina.portfolio.clientsservice.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PhoneRepositoryTest {

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private PhoneRepository phoneRepository;

    @Test
    void findByIdAndClientId_returnsEmpty_whenPhoneBelongsToDifferentClient() {
        var owner = clientRepository.save(new Client("Jane Doe", "jane@example.com"));
        var otherClient = clientRepository.save(new Client("John Roe", "john@example.com"));
        var phone = phoneRepository.save(new Phone(owner.getId(), "+1 555 0100", PhoneType.MOBILE, true));

        assertThat(phoneRepository.findByIdAndClientId(phone.getId(), otherClient.getId())).isEmpty();
        assertThat(phoneRepository.findByIdAndClientId(phone.getId(), owner.getId())).isPresent();
    }

    @Test
    void findByClientIdAndPrimaryTrue_returnsOnlyThePrimaryRow() {
        var owner = clientRepository.save(new Client("Jane Doe", "jane@example.com"));
        phoneRepository.save(new Phone(owner.getId(), "+1 555 0100", PhoneType.MOBILE, true));
        phoneRepository.save(new Phone(owner.getId(), "+1 555 0200", PhoneType.WORK, false));

        var primaries = phoneRepository.findByClientIdAndPrimaryTrue(owner.getId());

        assertThat(primaries).hasSize(1);
        assertThat(primaries.getFirst().getNumber()).isEqualTo("+1 555 0100");
    }

    @Test
    void findByClientId_paginatesResults() {
        var owner = clientRepository.save(new Client("Jane Doe", "jane@example.com"));
        phoneRepository.save(new Phone(owner.getId(), "+1 555 0100", PhoneType.MOBILE, true));
        phoneRepository.save(new Phone(owner.getId(), "+1 555 0200", PhoneType.WORK, false));

        var page = phoneRepository.findByClientId(owner.getId(), PageRequest.of(0, 1));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1);
    }
}
