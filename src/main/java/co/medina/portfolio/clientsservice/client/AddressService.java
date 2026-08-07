package co.medina.portfolio.clientsservice.client;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;
    private final ClientRepository clientRepository;

    @Transactional
    public Address create(UUID clientId, AddressRequest request) {
        requireClientExists(clientId);
        var primary = addressRepository.countByClientId(clientId) == 0 || request.primary();
        if (primary) {
            demotePrimaries(clientId, null);
        }
        return addressRepository.save(new Address(
                clientId,
                request.street(),
                request.city(),
                request.state(),
                request.postalCode(),
                request.country(),
                request.type(),
                primary));
    }

    @Transactional(readOnly = true)
    public Address getById(UUID clientId, UUID addressId) {
        requireClientExists(clientId);
        return addressRepository.findByIdAndClientId(addressId, clientId)
                .orElseThrow(() -> new NotFoundException("Address " + addressId + " not found for client " + clientId));
    }

    @Transactional(readOnly = true)
    public Page<Address> getAll(UUID clientId, Pageable pageable) {
        requireClientExists(clientId);
        return addressRepository.findByClientId(clientId, pageable);
    }

    @Transactional
    public Address update(UUID clientId, UUID addressId, AddressRequest request) {
        var address = getById(clientId, addressId);
        var soleRemaining = addressRepository.countByClientId(clientId) == 1;
        var primary = soleRemaining || request.primary();
        if (primary) {
            demotePrimaries(clientId, addressId);
        }
        address.setStreet(request.street());
        address.setCity(request.city());
        address.setState(request.state());
        address.setPostalCode(request.postalCode());
        address.setCountry(request.country());
        address.setType(request.type());
        address.setPrimary(primary);
        return addressRepository.save(address);
    }

    @Transactional
    public void delete(UUID clientId, UUID addressId) {
        addressRepository.delete(getById(clientId, addressId));
    }

    private void requireClientExists(UUID clientId) {
        if (!clientRepository.existsById(clientId)) {
            throw new NotFoundException("Client " + clientId + " not found");
        }
    }

    private void demotePrimaries(UUID clientId, UUID excludeId) {
        var primaries = addressRepository.findByClientIdAndPrimaryTrue(clientId).stream()
                .filter(address -> !address.getId().equals(excludeId))
                .toList();
        primaries.forEach(address -> address.setPrimary(false));
        addressRepository.saveAllAndFlush(primaries);
    }
}
