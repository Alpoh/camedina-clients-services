package co.medina.portfolio.clientsservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ClientRepository clientRepository;

    private ProjectService projectService;

    private final UUID clientId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        projectService = new ProjectService(projectRepository, clientRepository);
    }

    @Test
    void create_throwsNotFoundException_whenClientDoesNotExist() {
        when(clientRepository.existsById(clientId)).thenReturn(false);
        var request = new ProjectRequest("Website Redesign", "New marketing site", ProjectStatus.PLANNING);

        assertThatThrownBy(() -> projectService.create(clientId, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_savesProject_whenClientExists() {
        when(clientRepository.existsById(clientId)).thenReturn(true);
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var request = new ProjectRequest("Website Redesign", "New marketing site", ProjectStatus.PLANNING);

        var saved = projectService.create(clientId, request);

        assertThat(saved.getName()).isEqualTo("Website Redesign");
        assertThat(saved.getStatus()).isEqualTo(ProjectStatus.PLANNING);
    }

    @Test
    void findById_throwsNotFoundException_whenProjectBelongsToDifferentClient() {
        var projectId = UUID.randomUUID();
        when(clientRepository.existsById(clientId)).thenReturn(true);
        when(projectRepository.findByIdAndClientId(projectId, clientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.findById(clientId, projectId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void update_changesStatus() {
        var projectId = UUID.randomUUID();
        var project = new Project(clientId, "Website Redesign", "New marketing site", ProjectStatus.PLANNING);
        project.setId(projectId);
        when(clientRepository.existsById(clientId)).thenReturn(true);
        when(projectRepository.findByIdAndClientId(projectId, clientId)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var request = new ProjectRequest("Website Redesign", "New marketing site", ProjectStatus.IN_PROGRESS);

        var updated = projectService.update(clientId, projectId, request);

        assertThat(updated.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
    }

    @Test
    void delete_throwsNotFoundException_whenProjectUnknown() {
        var projectId = UUID.randomUUID();
        when(clientRepository.existsById(clientId)).thenReturn(true);
        when(projectRepository.findByIdAndClientId(projectId, clientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.delete(clientId, projectId))
                .isInstanceOf(NotFoundException.class);
    }
}
