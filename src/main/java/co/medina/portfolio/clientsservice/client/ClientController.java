package co.medina.portfolio.clientsservice.client;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    @PostMapping
    ResponseEntity<ClientResponse> create(@Valid @RequestBody ClientRequest request) {
        var client = clientService.create(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(client.getId())
                .toUri();
        return ResponseEntity.created(location).body(ClientResponse.from(client));
    }

    @GetMapping("/{id}")
    ClientResponse getById(@PathVariable UUID id) {
        return ClientResponse.from(clientService.getById(id));
    }

    @GetMapping
    Page<ClientResponse> getAll(Pageable pageable) {
        return clientService.getAll(pageable).map(ClientResponse::from);
    }

    @PutMapping("/{id}")
    ClientResponse update(@PathVariable UUID id, @Valid @RequestBody ClientRequest request) {
        return ClientResponse.from(clientService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        clientService.delete(id);
    }
}
