package co.medina.portfolio.clientsservice.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

// Confirms security.swagger.permit-all=true opens Swagger/OpenAPI docs, distinct from
// SecurityIntegrationTest which confirms they stay locked down by default. Sets the property directly
// rather than activating the "local" Spring profile: application-local.properties is gitignored (it's a
// personal, per-developer file, never committed - see .gitignore), so it doesn't exist in a fresh CI
// checkout, and @ActiveProfiles("local") alone would silently leave the property at its default there.
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "security.swagger.permit-all=true")
class SwaggerPermitAllIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void swaggerUi_isReachable_withoutToken_whenPermitAllTrue() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }
}
