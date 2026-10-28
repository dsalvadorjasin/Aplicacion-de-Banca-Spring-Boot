package com.coding.exercise.bankapp.v2.support;

import com.coding.exercise.bankapp.v2.ApiV2BadRequestException;
import com.coding.exercise.bankapp.v2.ApiV2BusinessRuleException;
import com.coding.exercise.bankapp.v2.ApiV2ConflictException;
import com.coding.exercise.bankapp.v2.ApiV2NotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:apiv2-advice;DB_CLOSE_DELAY=-1"})
@Import(ApiV2ExceptionHandlerTest.ProbeController.class)
class ApiV2ExceptionHandlerTest {
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;

    @Test
    void mapsV2DomainAndPersistenceErrorsToProblemDetails() throws Exception {
        assertProblem(get("/v2/__probe/not-found"), 404, "Not Found", "Missing probe.");
        assertProblem(get("/v2/__probe/conflict"), 409, "Conflict", "Conflict probe.");
        assertProblem(get("/v2/__probe/business-rule"), 422, "Unprocessable Entity", "Business rule probe.");
        assertProblem(get("/v2/__probe/bad-request"), 400, "Bad Request", "Bad request probe.");
        assertProblem(get("/v2/__probe/duplicate"), 409, "Conflict",
                "Duplicate records found for the requested identifier.");
    }

    @Test
    void catchesUnexpectedErrorsWithoutLeakingTheMessage() throws Exception {
        ResponseEntity<String> response = get("/v2/__probe/unexpected");
        assertProblem(response, 500, "Internal Server Error", "Unexpected error.");
        assertThat(response.getBody()).doesNotContain("do not disclose this");
    }

    @Test
    void mapsPathTypeMismatchAndMalformedJsonToBadRequest() throws Exception {
        assertProblem(get("/v2/__probe/id/abc"), 400, "Bad Request", null);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        assertProblem(http.exchange("/v2/__probe/body", HttpMethod.POST,
                new HttpEntity<>("{", headers), String.class), 400, "Bad Request", null);
    }

    @Test
    void unmatchedV2RequestsUseProblemDetailsAndV1RequestsKeepLegacyErrors() throws Exception {
        ResponseEntity<String> missingV2 = get("/v2/does-not-exist");
        assertProblem(missingV2, 404, "Not Found", null);
        assertThat(mapper.readTree(missingV2.getBody()).get("instance").asText())
                .isEqualTo("/bank-api/v2/does-not-exist");

        ResponseEntity<String> v2Root = get("/v2");
        assertProblem(v2Root, 404, "Not Found", null);

        ResponseEntity<String> wrongV2Method = http.exchange(
                "/v2/__probe/not-found", HttpMethod.DELETE, HttpEntity.EMPTY, String.class);
        assertProblem(wrongV2Method, 405, "Method Not Allowed", null);
        assertThat(wrongV2Method.getHeaders().getFirst(HttpHeaders.ALLOW)).contains("GET");

        ResponseEntity<String> missingV1 = get("/does-not-exist");
        assertLegacyError(missingV1, 404);
        ResponseEntity<String> wrongV1Method = http.exchange(
                "/accounts/transactions/1000", HttpMethod.DELETE, HttpEntity.EMPTY, String.class);
        assertLegacyError(wrongV1Method, 405);
        assertLegacyError(http.exchange(
                "/customers/all", HttpMethod.DELETE, HttpEntity.EMPTY, String.class), 400);

        assertSecurityHeaders(missingV2, missingV1);
    }

    @Test
    void v1ErrorsKeepTheirExistingShapeAndSecurityHeadersStayConsistent() throws Exception {
        ResponseEntity<String> v1 = get("/customers/abc");
        assertThat(v1.getStatusCode().value()).isEqualTo(400);
        assertThat(v1.getHeaders().getContentType()).isNotEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        JsonNode v1Body = mapper.readTree(v1.getBody());
        assertThat(v1Body.hasNonNull("error")).isTrue();
        assertThat(v1Body.hasNonNull("path")).isTrue();
        assertThat(v1Body.hasNonNull("timestamp")).isTrue();

        ResponseEntity<String> v2 = get("/v2/__probe/not-found");
        assertThat(v2.getHeaders().getFirst("X-XSS-Protection"))
                .isEqualTo(v1.getHeaders().getFirst("X-XSS-Protection"))
                .isEqualTo("1; mode=block");
        assertThat(v2.getHeaders().getFirst("X-Content-Type-Options"))
                .isEqualTo(v1.getHeaders().getFirst("X-Content-Type-Options"))
                .isEqualTo("nosniff");
        assertThat(v2.getHeaders().getFirst("X-Frame-Options")).isNull();
        assertThat(v2.getHeaders().getFirst("WWW-Authenticate")).isNull();
    }

    private void assertLegacyError(ResponseEntity<String> response, int status) throws Exception {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isNotEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        JsonNode body = mapper.readTree(response.getBody());
        assertThat(body.hasNonNull("error")).isTrue();
        assertThat(body.hasNonNull("path")).isTrue();
        assertThat(body.hasNonNull("timestamp")).isTrue();
    }

    private void assertSecurityHeaders(ResponseEntity<String> v2, ResponseEntity<String> v1) {
        assertThat(v2.getHeaders().getFirst("X-XSS-Protection"))
                .isEqualTo(v1.getHeaders().getFirst("X-XSS-Protection"))
                .isEqualTo("1; mode=block");
        assertThat(v2.getHeaders().getFirst("X-Content-Type-Options"))
                .isEqualTo(v1.getHeaders().getFirst("X-Content-Type-Options"))
                .isEqualTo("nosniff");
        assertThat(v2.getHeaders().getFirst("X-Frame-Options")).isNull();
        assertThat(v2.getHeaders().getFirst("WWW-Authenticate")).isNull();
    }

    private ResponseEntity<String> get(String path) {
        return http.getForEntity(path, String.class);
    }

    private void assertProblem(
            ResponseEntity<String> response, int status, String title, String detail) throws Exception {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        JsonNode body = mapper.readTree(response.getBody());
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.get("title").asText()).isEqualTo(title);
        assertThat(body.hasNonNull("detail")).isTrue();
        if (detail != null) {
            assertThat(body.get("detail").asText()).isEqualTo(detail);
        }
        assertThat(body.hasNonNull("instance")).isTrue();
    }

    @RestController
    @RequestMapping("/v2/__probe")
    public static class ProbeController {
        @GetMapping("/not-found")
        public void notFound() {
            throw new ApiV2NotFoundException("Missing probe.");
        }

        @GetMapping("/conflict")
        public void conflict() {
            throw new ApiV2ConflictException("Conflict probe.");
        }

        @GetMapping("/business-rule")
        public void businessRule() {
            throw new ApiV2BusinessRuleException("Business rule probe.");
        }

        @GetMapping("/bad-request")
        public void badRequest() {
            throw new ApiV2BadRequestException("Bad request probe.");
        }

        @GetMapping("/duplicate")
        public void duplicate() {
            throw new IncorrectResultSizeDataAccessException(1, 2);
        }

        @GetMapping("/unexpected")
        public void unexpected() {
            throw new IllegalStateException("do not disclose this");
        }

        @GetMapping("/id/{id}")
        public Long id(@PathVariable Long id) {
            return id;
        }

        @PostMapping("/body")
        public String body(@RequestBody ProbeBody body) {
            return body.value();
        }
    }

    public record ProbeBody(String value) {
    }
}
