package co.medina.portfolio.clientsservice.client;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, UUID> {

    Page<Address> findByClientId(UUID clientId, Pageable pageable);

    Optional<Address> findByIdAndClientId(UUID id, UUID clientId);

    long countByClientId(UUID clientId);

    List<Address> findByClientIdAndPrimaryTrue(UUID clientId);
}
