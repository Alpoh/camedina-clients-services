package co.medina.portfolio.clientsservice.client;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ClientRepository clientRepository;

    @Transactional
    public Project create(UUID clientId, ProjectRequest request) {
        requireClientExists(clientId);
        return projectRepository.save(
                new Project(clientId, request.name(), request.description(), request.status()));
    }

    @Transactional(readOnly = true)
    public Project findById(UUID clientId, UUID projectId) {
        requireClientExists(clientId);
        return projectRepository.findByIdAndClientId(projectId, clientId)
                .orElseThrow(() -> new NotFoundException("Project " + projectId + " not found for client " + clientId));
    }

    @Transactional(readOnly = true)
    public Page<Project> findAll(UUID clientId, Pageable pageable) {
        requireClientExists(clientId);
        return projectRepository.findByClientId(clientId, pageable);
    }

    @Transactional
    public Project update(UUID clientId, UUID projectId, ProjectRequest request) {
        var project = findById(clientId, projectId);
        project.setName(request.name());
        project.setDescription(request.description());
        project.setStatus(request.status());
        return projectRepository.save(project);
    }

    @Transactional
    public void delete(UUID clientId, UUID projectId) {
        projectRepository.delete(findById(clientId, projectId));
    }

    private void requireClientExists(UUID clientId) {
        if (!clientRepository.existsById(clientId)) {
            throw new NotFoundException("Client " + clientId + " not found");
        }
    }
}
