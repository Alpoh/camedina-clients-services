package co.medina.portfolio.clientsservice.client;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
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
@RequestMapping("/api/v1/clients/{clientId}/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @PostMapping
    ResponseEntity<AddressResponse> create(@PathVariable UUID clientId, @Valid @RequestBody AddressRequest request) {
        var address = addressService.create(clientId, request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(address.getId())
                .toUri();
        return ResponseEntity.created(location).body(AddressResponse.from(address));
    }

    @GetMapping("/{addressId}")
    AddressResponse findById(@PathVariable UUID clientId, @PathVariable UUID addressId) {
        return AddressResponse.from(addressService.findById(clientId, addressId));
    }

    @GetMapping
    Page<AddressResponse> findAll(@PathVariable UUID clientId, @ParameterObject Pageable pageable) {
        return addressService.findAll(clientId, pageable).map(AddressResponse::from);
    }

    @PutMapping("/{addressId}")
    AddressResponse update(
            @PathVariable UUID clientId, @PathVariable UUID addressId, @Valid @RequestBody AddressRequest request) {
        return AddressResponse.from(addressService.update(clientId, addressId, request));
    }

    @DeleteMapping("/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID clientId, @PathVariable UUID addressId) {
        addressService.delete(clientId, addressId);
    }
}
