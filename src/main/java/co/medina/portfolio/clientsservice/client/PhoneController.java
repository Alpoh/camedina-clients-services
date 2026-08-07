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
@RequestMapping("/api/v1/clients/{clientId}/phones")
@RequiredArgsConstructor
public class PhoneController {

    private final PhoneService phoneService;

    @PostMapping
    ResponseEntity<PhoneResponse> create(@PathVariable UUID clientId, @Valid @RequestBody PhoneRequest request) {
        var phone = phoneService.create(clientId, request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(phone.getId())
                .toUri();
        return ResponseEntity.created(location).body(PhoneResponse.from(phone));
    }

    @GetMapping("/{phoneId}")
    PhoneResponse getById(@PathVariable UUID clientId, @PathVariable UUID phoneId) {
        return PhoneResponse.from(phoneService.getById(clientId, phoneId));
    }

    @GetMapping
    Page<PhoneResponse> getAll(@PathVariable UUID clientId, Pageable pageable) {
        return phoneService.getAll(clientId, pageable).map(PhoneResponse::from);
    }

    @PutMapping("/{phoneId}")
    PhoneResponse update(@PathVariable UUID clientId, @PathVariable UUID phoneId, @Valid @RequestBody PhoneRequest request) {
        return PhoneResponse.from(phoneService.update(clientId, phoneId, request));
    }

    @DeleteMapping("/{phoneId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID clientId, @PathVariable UUID phoneId) {
        phoneService.delete(clientId, phoneId);
    }
}
