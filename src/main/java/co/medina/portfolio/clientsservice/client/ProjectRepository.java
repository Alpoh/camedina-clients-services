package co.medina.portfolio.clientsservice.client;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Page<Project> findByClientId(UUID clientId, Pageable pageable);

    Optional<Project> findByIdAndClientId(UUID id, UUID clientId);
}
