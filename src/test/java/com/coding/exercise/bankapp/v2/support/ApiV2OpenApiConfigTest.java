package com.coding.exercise.bankapp.v2.support;

import java.util.Set;
import java.util.HashSet;

import com.coding.exercise.bankapp.v2.support.ApiV2ExceptionHandlerTest.ProbeController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:apiv2-openapi;DB_CLOSE_DELAY=-1"})
@Import(ProbeController.class)
class ApiV2OpenApiConfigTest {
    private static final Set<String> V1_PATHS = Set.of(
            "/accounts/{accountNumber}",
            "/accounts/add/{customerNumber}",
            "/accounts/transactions/{accountNumber}",
            "/accounts/transfer/{customerNumber}",
            "/customers/{customerNumber}",
            "/customers/add",
            "/customers/all");
    private static final Set<String> V1_SCHEMAS = Set.of(
            "AddressDetails", "ContactDetails", "CustomerDetails", "TransferDetails",
            "AccountInformation", "BankInformation", "TransactionDetails");

    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;

    @Test
    void rootAndV1DocumentsContainOnlyV1OperationsAndReachableSchemas() throws Exception {
        JsonNode root = json("/v3/api-docs");
        JsonNode v1 = json("/v3/api-docs/v1");
        assertThat(fieldNames(root.get("paths"))).isEqualTo(V1_PATHS);
        assertThat(fieldNames(v1.get("paths"))).isEqualTo(V1_PATHS);
        assertThat(fieldNames(root.get("components").get("schemas"))).isEqualTo(V1_SCHEMAS);
        assertThat(root.has("tags")).isFalse();
    }

    @Test
    void v2GroupIncludesTheProbeWhileSwaggerConfigKeepsDefaultAndGroupUrls() throws Exception {
        JsonNode v2 = json("/v3/api-docs/v2");
        assertThat(fieldNames(v2.get("paths"))).contains(
                "/v2/__probe/not-found", "/v2/__probe/body");

        JsonNode swaggerConfig = json("/v3/api-docs/swagger-config");
        assertThat(swaggerConfig.get("url").asText()).isEqualTo("/bank-api/v3/api-docs");
        Set<String> groups = new HashSet<>();
        swaggerConfig.get("urls").forEach(url -> groups.add(url.get("name").asText()));
        assertThat(groups).contains("v1", "v2");

        ResponseEntity<String> swaggerUi = http.getForEntity("/swagger-ui/index.html", String.class);
        assertThat(swaggerUi.getStatusCode().value()).isEqualTo(200);
    }

    private JsonNode json(String path) throws Exception {
        ResponseEntity<String> response = http.getForEntity(path, String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return mapper.readTree(response.getBody());
    }

    private Set<String> fieldNames(JsonNode object) {
        Set<String> names = new HashSet<>();
        object.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
