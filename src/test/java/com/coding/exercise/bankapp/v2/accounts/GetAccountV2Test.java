package com.coding.exercise.bankapp.v2.accounts;

import com.coding.exercise.bankapp.v2.support.ApiV2Comparison;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:apiv2-accounts-get;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
class GetAccountV2Test extends AccountsV2TestSupport {
    @Test
    void foundAccountReturnsTheSameJsonWithOkInsteadOfFound() {
        long customerNumber = id();
        long accountNumber = id();
        createCustomer(customerNumber);
        createV1Account(customerNumber, accountNumber, "125.50");

        ApiV2Comparison.Responses responses = ApiV2Comparison.compare(
                http, HttpMethod.GET, "/accounts/" + accountNumber,
                "/v2/accounts/" + accountNumber, null);
        assertThat(responses.v1().getStatusCode().value()).isEqualTo(302);
        assertThat(responses.v2().getStatusCode().value()).isEqualTo(200);
        assertThat(responses.v2().getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        assertThat(responses.v2().getHeaders().getLocation()).isNull();
        assertValidV2(HttpMethod.GET, "/v2/accounts/" + accountNumber, null, responses.v2());
    }

    @Test
    void missingAccountUsesProblemDetailInsteadOfV1PlainText() throws Exception {
        long accountNumber = id();
        ResponseEntity<String> v1 = v1Exchange(HttpMethod.GET, "/accounts/" + accountNumber, null);
        ResponseEntity<String> v2 = exchange(HttpMethod.GET, "/v2/accounts/" + accountNumber, null);

        assertThat(v1.getStatusCode().value()).isEqualTo(404);
        assertThat(v1.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_PLAIN)).isTrue();
        assertThat(v1.getBody()).isEqualTo("Account Number " + accountNumber + " not found.");
        assertProblem(v2, 404, "Account Number " + accountNumber + " not found.",
                "/v2/accounts/" + accountNumber);
        assertInvalidV2(HttpMethod.GET, "/v2/accounts/" + accountNumber, v2);
    }

    @Test
    void nonNumericAccountNumberUsesProblemDetailInsteadOfBootError() throws Exception {
        ResponseEntity<String> v1 = exchange(HttpMethod.GET, "/accounts/nope", null);
        ResponseEntity<String> v2 = exchange(HttpMethod.GET, "/v2/accounts/nope", null);

        assertBootError(v1, 400, "/accounts/nope");
        assertProblem(v2, 400, null, "/v2/accounts/nope");
        assertInvalidV2(HttpMethod.GET, "/v2/accounts/nope", v2);
    }

    @Test
    void duplicateStoredAccountNumbersUseConflictInsteadOfV1ServerError() throws Exception {
        long customerNumber = id();
        long accountNumber = id();
        createCustomer(customerNumber);
        createV1Account(customerNumber, accountNumber, "10");
        createV1Account(customerNumber, accountNumber, "20");

        ResponseEntity<String> v1 = exchange(HttpMethod.GET, "/accounts/" + accountNumber, null);
        ResponseEntity<String> v2 = exchange(HttpMethod.GET, "/v2/accounts/" + accountNumber, null);

        assertBootError(v1, 500, "/accounts/" + accountNumber);
        assertProblem(v2, 409, "Duplicate records found for the requested identifier.",
                "/v2/accounts/" + accountNumber);
        assertInvalidV2(HttpMethod.GET, "/v2/accounts/" + accountNumber, v2);
    }

    @Test
    void nonFiniteStoredBalanceUsesConflictInsteadOfNonNumericV1Json() throws Exception {
        long customerNumber = id();
        long accountNumber = id();
        createCustomer(customerNumber);
        createV1Account(customerNumber, accountNumber, "1e309");

        ResponseEntity<String> v1 = exchange(HttpMethod.GET, "/accounts/" + accountNumber, null);
        ResponseEntity<String> v2 = exchange(HttpMethod.GET, "/v2/accounts/" + accountNumber, null);

        assertThat(v1.getStatusCode().value()).isEqualTo(302);
        assertThat(v1.getBody()).contains("\"accountBalance\":\"Infinity\"");
        assertThat(mapper.readTree(v1.getBody()).get("accountBalance").isTextual()).isTrue();
        assertThat(mapper.readTree(v1.getBody()).get("accountBalance").asText()).isEqualTo("Infinity");
        assertProblem(v2, 409,
                "Account Number " + accountNumber + " has a stored balance that is not a finite number.",
                "/v2/accounts/" + accountNumber);
        assertInvalidV2(HttpMethod.GET, "/v2/accounts/" + accountNumber, v2);
    }

    @Test
    void securityHeadersAndCredentialBehaviorMatchV1() {
        long customerNumber = id();
        long accountNumber = id();
        createCustomer(customerNumber);
        createV1Account(customerNumber, accountNumber, "9");

        for (TestRestTemplate client : new TestRestTemplate[]{
                http,
                http.withBasicAuth("bankapp", "changeit"),
                http.withBasicAuth("bankapp", "incorrect")}) {
            ResponseEntity<String> v1 = exchange(client, HttpMethod.GET, "/accounts/" + accountNumber, null);
            ResponseEntity<String> v2 = exchange(client, HttpMethod.GET, "/v2/accounts/" + accountNumber, null);
            assertThat(v1.getStatusCode().value()).isEqualTo(302);
            assertThat(v2.getStatusCode().value()).isEqualTo(200);
            assertValidV2(HttpMethod.GET, "/v2/accounts/" + accountNumber, null, v2);
            for (String header : new String[]{
                    "X-XSS-Protection", "X-Content-Type-Options", "Cache-Control", "Pragma", "Expires"}) {
                assertThat(v1.getHeaders().getFirst(header)).isEqualTo(v2.getHeaders().getFirst(header));
            }
            assertThat(v1.getHeaders().getFirst("X-XSS-Protection")).isEqualTo("1; mode=block");
            assertThat(v1.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(v1.getHeaders().getFirst("Cache-Control"))
                    .isEqualTo("no-cache, no-store, max-age=0, must-revalidate");
            assertThat(v1.getHeaders().getFirst("Pragma")).isEqualTo("no-cache");
            assertThat(v1.getHeaders().getFirst("Expires")).isEqualTo("0");
            assertThat(v1.getHeaders().getFirst("WWW-Authenticate")).isNull();
            assertThat(v2.getHeaders().getFirst("WWW-Authenticate")).isNull();
            assertThat(v1.getHeaders().getFirst("X-Frame-Options")).isNull();
            assertThat(v2.getHeaders().getFirst("X-Frame-Options")).isNull();
        }
    }
}
