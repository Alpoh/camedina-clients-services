package co.medina.portfolio.clientsservice.client;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PhoneService {

    private final PhoneRepository phoneRepository;
    private final ClientRepository clientRepository;

    @Transactional
    public Phone create(UUID clientId, PhoneRequest request) {
        requireClientExists(clientId);
        var primary = phoneRepository.countByClientId(clientId) == 0 || request.primary();
        if (primary) {
            demotePrimaries(clientId, null);
        }
        return phoneRepository.save(new Phone(clientId, request.number(), request.type(), primary));
    }

    @Transactional(readOnly = true)
    public Phone findById(UUID clientId, UUID phoneId) {
        requireClientExists(clientId);
        return phoneRepository.findByIdAndClientId(phoneId, clientId)
                .orElseThrow(() -> new NotFoundException("Phone " + phoneId + " not found for client " + clientId));
    }

    @Transactional(readOnly = true)
    public Page<Phone> findAll(UUID clientId, Pageable pageable) {
        requireClientExists(clientId);
        return phoneRepository.findByClientId(clientId, pageable);
    }

    @Transactional
    public Phone update(UUID clientId, UUID phoneId, PhoneRequest request) {
        var phone = findById(clientId, phoneId);
        var soleRemaining = phoneRepository.countByClientId(clientId) == 1;
        var primary = soleRemaining || request.primary();
        if (primary) {
            demotePrimaries(clientId, phoneId);
        }
        phone.setNumber(request.number());
        phone.setType(request.type());
        phone.setPrimary(primary);
        return phoneRepository.save(phone);
    }

    @Transactional
    public void delete(UUID clientId, UUID phoneId) {
        phoneRepository.delete(findById(clientId, phoneId));
    }

    private void requireClientExists(UUID clientId) {
        if (!clientRepository.existsById(clientId)) {
            throw new NotFoundException("Client " + clientId + " not found");
        }
    }

    private void demotePrimaries(UUID clientId, UUID excludeId) {
        var primaries = phoneRepository.findByClientIdAndPrimaryTrue(clientId).stream()
                .filter(phone -> !phone.getId().equals(excludeId))
                .toList();
        primaries.forEach(phone -> phone.setPrimary(false));
        phoneRepository.saveAllAndFlush(primaries);
    }
}
