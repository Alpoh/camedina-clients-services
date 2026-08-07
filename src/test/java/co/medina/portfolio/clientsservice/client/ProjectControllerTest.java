package co.medina.portfolio.clientsservice.client;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(ProjectController.class)
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProjectService projectService;

    @Test
    void create_returns201WithLocationHeader_andLowercaseStatusInBody() throws Exception {
        var clientId = UUID.randomUUID();
        var project = new Project(clientId, "Website Redesign", "New marketing site", ProjectStatus.PLANNING);
        when(projectService.create(eq(clientId), any(ProjectRequest.class))).thenReturn(project);
        var body = objectMapper.writeValueAsString(
                new ProjectRequest("Website Redesign", "New marketing site", ProjectStatus.PLANNING));

        mockMvc.perform(post("/api/v1/clients/{clientId}/projects", clientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/projects/")))
                .andExpect(jsonPath("$.status").value("planning"));
    }

    @Test
    void create_returns404_whenParentClientDoesNotExist() throws Exception {
        var clientId = UUID.randomUUID();
        when(projectService.create(eq(clientId), any(ProjectRequest.class)))
                .thenThrow(new NotFoundException("Client " + clientId + " not found"));
        var body = objectMapper.writeValueAsString(
                new ProjectRequest("Website Redesign", "New marketing site", ProjectStatus.PLANNING));

        mockMvc.perform(post("/api/v1/clients/{clientId}/projects", clientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_returns400_whenNameBlank() throws Exception {
        var clientId = UUID.randomUUID();
        var body = objectMapper.writeValueAsString(new ProjectRequest("", null, ProjectStatus.PLANNING));

        mockMvc.perform(post("/api/v1/clients/{clientId}/projects", clientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_returns400ProblemDetail_notLeakedStackTrace_whenStatusValueInvalid() throws Exception {
        var clientId = UUID.randomUUID();
        var body = "{\"name\":\"Bad Status Test\",\"status\":\"not_a_real_status\"}";

        mockMvc.perform(post("/api/v1/clients/{clientId}/projects", clientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Malformed JSON request body"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void getById_acceptsLowercaseStatusOnTheWire() throws Exception {
        var clientId = UUID.randomUUID();
        var projectId = UUID.randomUUID();
        var project = new Project(clientId, "Website Redesign", null, ProjectStatus.IN_PROGRESS);
        when(projectService.findById(clientId, projectId)).thenReturn(project);

        mockMvc.perform(get("/api/v1/clients/{clientId}/projects/{projectId}", clientId, projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("in_progress"));
    }

    @Test
    void findById_returns404_whenProjectBelongsToDifferentClient() throws Exception {
        var clientId = UUID.randomUUID();
        var projectId = UUID.randomUUID();
        when(projectService.findById(clientId, projectId))
                .thenThrow(new NotFoundException("Project " + projectId + " not found for client " + clientId));

        mockMvc.perform(get("/api/v1/clients/{clientId}/projects/{projectId}", clientId, projectId))
                .andExpect(status().isNotFound());
    }
}
