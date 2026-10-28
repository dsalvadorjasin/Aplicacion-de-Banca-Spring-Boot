package com.coding.exercise.bankapp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.net.HttpURLConnection;
import java.io.IOException;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:security-http;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"})
class SecurityHttpTest extends HttpTestSupport {
    @ParameterizedTest
    @ValueSource(strings = {"none", "correct", "wrong"})
    void credentialsDoNotProtectApi(String credentials) {
        TestRestTemplate client = credentials.equals("none") ? http : http.withBasicAuth("bankapp", credentials.equals("correct") ? "changeit" : "incorrect");
        var response = client.getForEntity("/accounts/" + id(), String.class);
        assertThat(response.getStatusCodeValue()).isEqualTo(404);
        assertThat(response.getBody()).matches("Account Number [0-9]+ not found\\.");
        assertHeaders(response);
    }

    @Test void csrfIsDisabledForPostPutDelete() {
        long c = id(); createCustomer(c);
        long absent = id();
        text(request(HttpMethod.PUT, "/customers/" + absent, customer(absent)), 404, "Customer Number " + absent + " not found.");
        text(request(HttpMethod.DELETE, "/customers/" + absent, null), 400, "Customer does not exist.");
    }

    @Test void publicRootConsoleActuatorAndDocumentation() throws Exception {
        error(get("/"), 404, "/");
        var console = get("/h2-console/");
        assertThat(console.getStatusCodeValue()).isEqualTo(200);
        assertThat(console.getBody()).contains("H2 Console");
        assertThat(console.getHeaders().getFirst("X-Frame-Options")).isNull();
        assertThat(console.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(console.getHeaders().getFirst("Cache-Control")).isEqualTo("no-cache");
        assertThat(console.getHeaders().getFirst("WWW-Authenticate")).isNull();
        var health = get("/actuator/health");
        text(health, 200, "{\"status\":\"UP\"}");
        assertHeaders(health);
        error(get("/actuator/env"), 404, "/actuator/env");
        var docs = get("/v3/api-docs");
        assertThat(docs.getStatusCodeValue()).isEqualTo(200);
        assertThat(json(docs).get("info").get("title").asText()).isEqualTo("BANKING APPLICATION REST API");
        assertThat(json(docs).get("paths").size()).isEqualTo(7);
        assertHeaders(docs);
        var swagger = get("/swagger-ui/index.html");
        assertThat(swagger.getStatusCodeValue()).isEqualTo(200);
        assertThat(swagger.getBody()).contains("Swagger UI");
        assertHeaders(swagger);
        TestRestTemplate noRedirect = new TestRestTemplate(new RestTemplateBuilder()
            .rootUri(http.getRootUri()).requestFactory(() -> new SimpleClientHttpRequestFactory() {
                @Override protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
                    super.prepareConnection(connection, method);
                    connection.setInstanceFollowRedirects(false);
                }
            }));
        var redirect = noRedirect.getForEntity("/swagger-ui.html", String.class);
        assertThat(redirect.getStatusCodeValue()).isEqualTo(302);
        assertThat(redirect.getHeaders().getLocation().toString()).isEqualTo("/bank-api/swagger-ui/index.html");
    }

    private void assertHeaders(ResponseEntity<String> response) {
        HttpHeaders h = response.getHeaders();
        assertThat(h.getFirst("WWW-Authenticate")).isNull();
        assertThat(h.getFirst("X-Frame-Options")).isNull();
        assertThat(h.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(h.getFirst("X-XSS-Protection")).isEqualTo("1; mode=block");
        assertThat(h.getFirst("Cache-Control")).isEqualTo("no-cache, no-store, max-age=0, must-revalidate");
        assertThat(h.getFirst("Pragma")).isEqualTo("no-cache");
        assertThat(h.getFirst("Expires")).isEqualTo("0");
        assertThat(h.getFirst("Strict-Transport-Security")).isNull();
    }
}
