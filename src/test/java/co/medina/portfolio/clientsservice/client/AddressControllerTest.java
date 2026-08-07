package co.medina.portfolio.clientsservice.client;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(AddressController.class)
class AddressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AddressService addressService;

    @Test
    void create_returns404_whenParentClientDoesNotExist() throws Exception {
        var clientId = UUID.randomUUID();
        when(addressService.create(eq(clientId), any(AddressRequest.class)))
                .thenThrow(new NotFoundException("Client " + clientId + " not found"));
        var body = objectMapper.writeValueAsString(
                new AddressRequest("1 Main St", "Springfield", null, "12345", "US", AddressType.HOME, false));

        mockMvc.perform(post("/api/v1/clients/{clientId}/addresses", clientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void findById_returns404_whenAddressBelongsToDifferentClient() throws Exception {
        var clientId = UUID.randomUUID();
        var addressId = UUID.randomUUID();
        when(addressService.findById(clientId, addressId))
                .thenThrow(new NotFoundException("Address " + addressId + " not found for client " + clientId));

        mockMvc.perform(get("/api/v1/clients/{clientId}/addresses/{addressId}", clientId, addressId))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_returns400_whenCountryNotIsoAlpha2() throws Exception {
        var clientId = UUID.randomUUID();
        var body = objectMapper.writeValueAsString(
                new AddressRequest("1 Main St", "Springfield", null, "12345", "USA", AddressType.HOME, false));

        mockMvc.perform(post("/api/v1/clients/{clientId}/addresses", clientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
