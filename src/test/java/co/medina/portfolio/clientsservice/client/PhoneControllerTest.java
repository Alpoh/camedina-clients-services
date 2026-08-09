package co.medina.portfolio.clientsservice.client;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.medina.portfolio.clientsservice.common.NotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(PhoneController.class)
@AutoConfigureMockMvc(addFilters = false)
class PhoneControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PhoneService phoneService;

    @Test
    void create_returns404_whenParentClientDoesNotExist() throws Exception {
        var clientId = UUID.randomUUID();
        when(phoneService.create(eq(clientId), any(PhoneRequest.class)))
                .thenThrow(new NotFoundException("Client " + clientId + " not found"));
        var body = objectMapper.writeValueAsString(new PhoneRequest("+1 555 0100", PhoneType.MOBILE, false));

        mockMvc.perform(post("/api/v1/clients/{clientId}/phones", clientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void findById_returns404_whenPhoneBelongsToDifferentClient() throws Exception {
        var clientId = UUID.randomUUID();
        var phoneId = UUID.randomUUID();
        when(phoneService.findById(clientId, phoneId))
                .thenThrow(new NotFoundException("Phone " + phoneId + " not found for client " + clientId));

        mockMvc.perform(get("/api/v1/clients/{clientId}/phones/{phoneId}", clientId, phoneId))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_returns400_whenNumberBlank() throws Exception {
        var clientId = UUID.randomUUID();
        var body = objectMapper.writeValueAsString(new PhoneRequest("", PhoneType.MOBILE, false));

        mockMvc.perform(post("/api/v1/clients/{clientId}/phones", clientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
