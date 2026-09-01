package co.medina.portfolio.clientsservice.client;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.medina.portfolio.clientsservice.common.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(ClientController.class)
@AutoConfigureMockMvc(addFilters = false)
class ClientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ClientService clientService;

    @Test
    void create_returns201WithLocationHeader_whenRequestValid() throws Exception {
        var client = new Client("Jane Doe", "jane@example.com");
        when(clientService.create(any(ClientRequest.class))).thenReturn(client);
        var body = objectMapper.writeValueAsString(new ClientRequest("Jane Doe", "jane@example.com"));

        mockMvc.perform(post("/api/v1/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/clients/")));
    }

    @Test
    void create_returns400ProblemDetail_whenEmailBlank() throws Exception {
        var body = objectMapper.writeValueAsString(new ClientRequest("Jane Doe", ""));

        mockMvc.perform(post("/api/v1/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void findById_returns404ProblemDetail_whenClientNotFound() throws Exception {
        var id = UUID.randomUUID();
        when(clientService.findById(id)).thenThrow(new NotFoundException("Client " + id + " not found"));

        mockMvc.perform(get("/api/v1/clients/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void findByEmail_returns404ProblemDetail_whenClientNotFound() throws Exception {
        when(clientService.findByEmail("jane@example.com"))
                .thenThrow(new NotFoundException("Client with email jane@example.com not found"));

        mockMvc.perform(get("/api/v1/clients/by-email/{email}", "jane@example.com"))
                .andExpect(status().isNotFound());
    }

    @Test
    void findAll_returns200WithPagedBody() throws Exception {
        when(clientService.findAll(any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/clients"))
                .andExpect(status().isOk());
    }

    @Test
    void delete_returns204_whenClientExists() throws Exception {
        mockMvc.perform(delete("/api/v1/clients/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }
}
