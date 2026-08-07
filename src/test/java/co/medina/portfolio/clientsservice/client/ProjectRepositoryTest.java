package co.medina.portfolio.clientsservice.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProjectRepositoryTest {

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Test
    void findByIdAndClientId_returnsEmpty_whenProjectBelongsToDifferentClient() {
        var owner = clientRepository.save(new Client("Jane Doe", "jane@example.com"));
        var otherClient = clientRepository.save(new Client("John Roe", "john@example.com"));
        var project = projectRepository.save(
                new Project(owner.getId(), "Website Redesign", null, ProjectStatus.PLANNING));

        assertThat(projectRepository.findByIdAndClientId(project.getId(), otherClient.getId())).isEmpty();
        assertThat(projectRepository.findByIdAndClientId(project.getId(), owner.getId())).isPresent();
    }

    @Test
    void findByClientId_paginatesResults() {
        var owner = clientRepository.save(new Client("Jane Doe", "jane@example.com"));
        projectRepository.save(new Project(owner.getId(), "Website Redesign", null, ProjectStatus.PLANNING));
        projectRepository.save(new Project(owner.getId(), "Mobile App", null, ProjectStatus.IN_PROGRESS));

        var page = projectRepository.findByClientId(owner.getId(), PageRequest.of(0, 1));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1);
    }
}
