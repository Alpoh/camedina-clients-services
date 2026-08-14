package co.medina.portfolio.clientsservice.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// Confirms the "local" profile (application-local.properties) opens Swagger/OpenAPI docs, distinct from
// SecurityIntegrationTest which confirms they stay locked down without it.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class SwaggerLocalProfileIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void swaggerUi_isReachable_withoutToken_inLocalProfile() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }
}
