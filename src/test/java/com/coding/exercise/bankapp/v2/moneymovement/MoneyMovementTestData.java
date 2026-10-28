package com.coding.exercise.bankapp.v2.moneymovement;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.coding.exercise.bankapp.v2.support.ApiV2SpecValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyMovementTestData {
    private static final AtomicLong IDS = new AtomicLong(700_000L);
    private static final ApiV2SpecValidator SPEC = ApiV2SpecValidator.forGroup("money-movement");

    private final TestRestTemplate http;
    private final ObjectMapper mapper;

    MoneyMovementTestData(TestRestTemplate http, ObjectMapper mapper) {
        this.http = http;
        this.mapper = mapper;
    }

    long id() {
        return IDS.incrementAndGet();
    }

    long customer() {
        long customerNumber = id();
        createCustomer(customerNumber);
        return customerNumber;
    }

    long account(long customerNumber, Double balance) {
        long accountNumber = id();
        createAccount(customerNumber, accountNumber, balance);
        return accountNumber;
    }

    void createCustomer(long customerNumber) {
        String body = "{\"customerNumber\":" + customerNumber
                + ",\"firstName\":\"Ada\",\"middleName\":\"M\",\"lastName\":\"Lovelace\","
                + "\"status\":\"ACTIVE\",\"customerAddress\":" + address()
                + ",\"contactDetails\":{\"emailId\":\"ada@example.test\","
                + "\"homePhone\":\"123\",\"workPhone\":\"456\"}}";
        assertThat(exchange(HttpMethod.POST, "/customers/add", body).getStatusCode().value()).isEqualTo(201);
    }

    ResponseEntity<String> createAccount(long customerNumber, long accountNumber, Double balance) {
        String balanceField = balance == null ? "" : ",\"accountBalance\":" + Double.toString(balance);
        String body = "{\"accountNumber\":" + accountNumber + balanceField
                + ",\"accountStatus\":\"ACTIVE\",\"accountType\":\"SAVINGS\","
                + "\"bankInformation\":{\"branchName\":\"Main\",\"branchCode\":12,\"routingNumber\":34,"
                + "\"branchAddress\":" + address() + "}}";
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/accounts/add/" + customerNumber, body);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return response;
    }

    ResponseEntity<String> exchange(HttpMethod method, String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    ResponseEntity<String> v2Valid(HttpMethod method, String path, String body) {
        ResponseEntity<String> response = exchange(method, path, body);
        validateV2(method, path, body, response);
        return response;
    }

    void validateV2(
            HttpMethod method, String path, String body, ResponseEntity<String> response) {
        SPEC.assertValid(method, path, body, response);
    }

    ResponseEntity<String> v2Invalid(HttpMethod method, String path, String body) {
        ResponseEntity<String> response = exchange(method, path, body);
        SPEC.assertValidResponse(method, path, response);
        return response;
    }

    JsonNode json(ResponseEntity<String> response) throws Exception {
        return mapper.readTree(response.getBody());
    }

    ResponseEntity<String> readAccount(long accountNumber) {
        ResponseEntity<String> response = exchange(HttpMethod.GET, "/accounts/" + accountNumber, null);
        assertThat(response.getStatusCode().value()).isEqualTo(302);
        return response;
    }

    Double balance(long accountNumber) throws Exception {
        JsonNode account = json(readAccount(accountNumber));
        return account.hasNonNull("accountBalance") ? account.get("accountBalance").asDouble() : null;
    }

    void assertBalance(long accountNumber, Double expected) throws Exception {
        assertThat(balance(accountNumber)).isEqualTo(expected);
    }

    JsonNode transactions(long accountNumber) throws Exception {
        ResponseEntity<String> response = exchange(
                HttpMethod.GET, "/accounts/transactions/" + accountNumber, null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return json(response);
    }

    void assertNoTransactions(long... accountNumbers) throws Exception {
        for (long accountNumber : accountNumbers) {
            assertThat(transactions(accountNumber)).isEmpty();
        }
    }

    void assertProblem(
            ResponseEntity<String> response, int status, String detail, String requestPath) throws Exception {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType()
                .isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
        JsonNode body = json(response);
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.get("title").asText()).isEqualTo(
                org.springframework.http.HttpStatus.valueOf(status).getReasonPhrase());
        if (detail == null) {
            assertThat(body.get("detail").asText()).isNotBlank();
        } else {
            assertThat(body.get("detail").asText()).isEqualTo(detail);
        }
        assertThat(body.get("instance").asText()).isEqualTo("/bank-api" + requestPath);
    }

    void assertProblemHasNoBootFields(ResponseEntity<String> response) throws Exception {
        JsonNode body = json(response);
        assertThat(body.has("error")).isFalse();
        assertThat(body.has("path")).isFalse();
    }

    void assertBootError(ResponseEntity<String> response, int status, String path) throws Exception {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        JsonNode body = json(response);
        assertThat(body.hasNonNull("error")).isTrue();
        assertThat(body.hasNonNull("path")).isTrue();
        assertThat(body.get("path").asText()).isEqualTo("/bank-api" + path);
    }

    String transferBody(Long fromAccountNumber, Long toAccountNumber, Double amount) {
        StringBuilder body = new StringBuilder("{");
        if (fromAccountNumber != null) {
            body.append("\"fromAccountNumber\":").append(fromAccountNumber).append(",");
        }
        if (toAccountNumber != null) {
            body.append("\"toAccountNumber\":").append(toAccountNumber).append(",");
        }
        if (amount != null) {
            body.append("\"transferAmount\":").append(Double.toString(amount)).append(",");
        }
        if (body.charAt(body.length() - 1) == ',') {
            body.setLength(body.length() - 1);
        }
        return body.append("}").toString();
    }

    private String address() {
        return "{\"address1\":\"1 Main\",\"address2\":\"Suite 2\",\"city\":\"Boston\","
                + "\"state\":\"MA\",\"zip\":\"02101\",\"country\":\"US\"}";
    }
}
