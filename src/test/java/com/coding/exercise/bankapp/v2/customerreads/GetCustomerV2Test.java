package com.coding.exercise.bankapp.v2.customerreads;

import com.coding.exercise.bankapp.model.Customer;
import com.coding.exercise.bankapp.v2.support.ApiV2Comparison;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GetCustomerV2Test extends CustomerReadsV2TestSupport {

    @Test
    void existingCustomerMatchesV1() throws Exception {
        long n = uniqueNumber();
        createCustomerViaV1(n);

        ApiV2Comparison.Responses responses = ApiV2Comparison.compare(
                http, HttpMethod.GET, "/customers/" + n, "/v2/customers/" + n, null);

        assertThat(responses.v1().getStatusCode().value()).isEqualTo(200);
        assertThat(responses.v2().getStatusCode().value()).isEqualTo(200);
        assertJsonContentType(responses.v1());
        assertJsonContentType(responses.v2());
        JsonNode body = json(responses.v2());
        assertThat(body.get("customerNumber").asLong()).isEqualTo(n);
        assertThat(body.get("firstName").asText()).isEqualTo("Ada");
        assertThat(body.at("/customerAddress/city").asText()).isEqualTo("Boston");
        assertThat(body.at("/contactDetails/emailId").asText()).isEqualTo("ada@example.test");
        SPEC.assertValid(HttpMethod.GET, "/v2/customers/" + n, null, responses.v2());
    }

    @Test
    void missingCustomerIs404ProblemInsteadOfV1EmptyOk() throws Exception {
        long n = uniqueNumber();

        ResponseEntity<String> v1 = get("/customers/" + n);
        assertThat(v1.getStatusCode().value()).isEqualTo(200);
        assertThat(v1.getBody()).isNull();

        ResponseEntity<String> v2 = get("/v2/customers/" + n);
        assertProblem(v2, 404, "/v2/customers/" + n);
        assertThat(json(v2).get("detail").asText()).contains(String.valueOf(n));
        SPEC.assertValid(HttpMethod.GET, "/v2/customers/" + n, null, v2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nope", "1.5", "99999999999999999999", "-99999999999999999999"})
    void invalidCustomerNumberIs400ProblemInsteadOfV1BootError(String customerNumber) throws Exception {
        assertV1BootError(get("/customers/" + customerNumber), 400, "/customers/" + customerNumber);

        ResponseEntity<String> v2 = get("/v2/customers/" + customerNumber);
        assertProblem(v2, 400, "/v2/customers/" + customerNumber);
        SPEC.assertValidResponse(HttpMethod.GET, "/v2/customers/" + customerNumber, v2);
    }

    @Test
    void duplicateStoredCustomerNumberIs409ProblemInsteadOfV1ServerError() throws Exception {
        long n = uniqueNumber();
        createCustomerViaV1(n);
        createCustomerViaV1(n);

        assertV1BootError(get("/customers/" + n), 500, "/customers/" + n);

        ResponseEntity<String> v2 = get("/v2/customers/" + n);
        assertProblem(v2, 409, "/v2/customers/" + n);
        SPEC.assertValid(HttpMethod.GET, "/v2/customers/" + n, null, v2);
        assertThat(entriesWithNumber(json(get("/v2/customers/all")), n)).hasSize(2);
    }

    @Test
    void storedCustomerWithoutAddressOrContactIsReturnedInsteadOfV1ServerError() throws Exception {
        long n = uniqueNumber();
        Customer stored = storeCustomerWithoutAddressOrContact(n);
        try {
            assertV1BootError(get("/customers/" + n), 500, "/customers/" + n);

            ResponseEntity<String> v2 = get("/v2/customers/" + n);
            assertThat(v2.getStatusCode().value()).isEqualTo(200);
            assertJsonContentType(v2);
            JsonNode body = json(v2);
            assertThat(body.get("customerNumber").asLong()).isEqualTo(n);
            assertThat(body.get("firstName").asText()).isEqualTo("Grace");
            assertThat(body.get("lastName").asText()).isEqualTo("Hopper");
            assertThat(body.get("middleName").isNull()).isTrue();
            assertThat(body.get("status").asText()).isEqualTo("ACTIVE");
            assertThat(body.get("customerAddress").isNull()).isTrue();
            assertThat(body.get("contactDetails").isNull()).isTrue();
            SPEC.assertValid(HttpMethod.GET, "/v2/customers/" + n, null, v2);
        } finally {
            customerRepository.delete(stored);
        }
    }

    @Test
    void securityBehaviourAndHeadersMatchV1() {
        long n = uniqueNumber();
        createCustomerViaV1(n);
        String[] securityHeaders = {"WWW-Authenticate", "X-Frame-Options", "X-Content-Type-Options",
                "X-XSS-Protection", "Cache-Control", "Pragma", "Expires", "Strict-Transport-Security"};
        String[][] pairs = {{"/customers/" + n, "/v2/customers/" + n},
                {"/customers/" + uniqueNumber(), "/v2/customers/" + uniqueNumber()},
                {"/customers/all", "/v2/customers/all"}};
        for (var client : new org.springframework.boot.test.web.client.TestRestTemplate[]{
                http, http.withBasicAuth("bankapp", "changeit"), http.withBasicAuth("bankapp", "wrong")}) {
            for (String[] pair : pairs) {
                HttpHeaders v1 = client.getForEntity(pair[0], String.class).getHeaders();
                ResponseEntity<String> v2 = client.getForEntity(pair[1], String.class);
                assertThat(v2.getStatusCode().value()).isIn(200, 404);
                for (String header : securityHeaders) {
                    assertThat(v2.getHeaders().get(header)).as(pair[1] + " " + header).isEqualTo(v1.get(header));
                }
            }
        }
        HttpHeaders v2 = get("/v2/customers/" + n).getHeaders();
        assertThat(v2.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(v2.getFirst("X-XSS-Protection")).isEqualTo("1; mode=block");
        assertThat(v2.getFirst("WWW-Authenticate")).isNull();
    }
}
