package co.medina.portfolio.clientsservice.client;

import co.medina.portfolio.clientsservice.common.ConflictException;
import co.medina.portfolio.clientsservice.common.NotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;

    @Transactional
    public Client create(ClientRequest request) {
        requireEmailAvailable(request.email());
        try {
            return clientRepository.save(new Client(request.name(), request.email()));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("A client with email " + request.email() + " already exists");
        }
    }

    @Transactional(readOnly = true)
    public Client findById(UUID id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Client " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public Page<Client> findAll(Pageable pageable) {
        return clientRepository.findAll(pageable);
    }

    @Transactional
    public Client update(UUID id, ClientRequest request) {
        var client = findById(id);
        if (!client.getEmail().equals(request.email())) {
            requireEmailAvailable(request.email());
        }
        client.setName(request.name());
        client.setEmail(request.email());
        try {
            return clientRepository.save(client);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("A client with email " + request.email() + " already exists");
        }
    }

    @Transactional
    public void delete(UUID id) {
        clientRepository.delete(findById(id));
    }

    private void requireEmailAvailable(String email) {
        if (clientRepository.existsByEmail(email)) {
            throw new ConflictException("A client with email " + email + " already exists");
        }
    }
}
