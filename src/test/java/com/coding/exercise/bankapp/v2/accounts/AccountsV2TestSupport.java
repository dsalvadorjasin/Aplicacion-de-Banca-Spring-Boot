package com.coding.exercise.bankapp.v2.accounts;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import com.coding.exercise.bankapp.model.CustomerAccountXRef;
import com.coding.exercise.bankapp.repository.CustomerAccountXRefRepository;
import com.coding.exercise.bankapp.v2.support.ApiV2Comparison;
import com.coding.exercise.bankapp.v2.support.ApiV2SpecValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

abstract class AccountsV2TestSupport {
    private static final AtomicLong IDS = new AtomicLong(
            7_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L));

    static final ApiV2SpecValidator SPEC = ApiV2SpecValidator.forGroup("accounts");

    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;
    @Autowired CustomerAccountXRefRepository xrefRepository;

    long id() {
        return IDS.incrementAndGet();
    }

    ResponseEntity<String> exchange(HttpMethod method, String path, String body) {
        return ApiV2Comparison.exchange(http, method, path, body);
    }

    ResponseEntity<String> exchange(
            TestRestTemplate client, HttpMethod method, String path, String body) {
        return ApiV2Comparison.exchange(client, method, path, body);
    }

    ResponseEntity<String> exchangeWithContentType(
            HttpMethod method, String path, String body, MediaType contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    ResponseEntity<String> v1Exchange(HttpMethod method, String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    void assertValidV2(
            HttpMethod method, String path, String requestBody, ResponseEntity<String> response) {
        SPEC.assertValid(method, path, requestBody, response);
    }

    void assertInvalidV2(HttpMethod method, String path, ResponseEntity<String> response) {
        SPEC.assertValidResponse(method, path, response);
    }

    void assertProblem(ResponseEntity<String> response, int status, String expectedDetailOrNull, String path)
            throws Exception {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .isTrue();
        JsonNode body = mapper.readTree(response.getBody());
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.get("title").asText()).isEqualTo(org.springframework.http.HttpStatus.valueOf(status).getReasonPhrase());
        if (expectedDetailOrNull != null) {
            assertThat(body.get("detail").asText()).isEqualTo(expectedDetailOrNull);
        }
        assertThat(body.get("instance").asText()).isEqualTo("/bank-api" + path);
    }

    void assertBootError(ResponseEntity<String> response, int status, String path) throws Exception {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        JsonNode body = mapper.readTree(response.getBody());
        assertThat(body.hasNonNull("error")).isTrue();
        assertThat(body.get("path").asText()).isEqualTo("/bank-api" + path);
        assertThat(body.hasNonNull("timestamp")).isTrue();
        assertThat(body.has("detail")).isFalse();
    }

    String address() {
        return "{\"address1\":\"1 Main\",\"address2\":\"Suite 2\",\"city\":\"Boston\","
                + "\"state\":\"MA\",\"zip\":\"02101\",\"country\":\"US\"}";
    }

    String customer(long customerNumber) {
        return "{\"customerNumber\":" + customerNumber
                + ",\"firstName\":\"Ada\",\"middleName\":\"M\",\"lastName\":\"Lovelace\","
                + "\"status\":\"ACTIVE\",\"customerAddress\":" + address()
                + ",\"contactDetails\":{\"emailId\":\"ada@example.test\","
                + "\"homePhone\":\"123\",\"workPhone\":\"456\"}}";
    }

    String account(long accountNumber, String balance) {
        return "{\"accountNumber\":" + accountNumber + ",\"accountBalance\":" + balance
                + ",\"accountStatus\":\"ACTIVE\",\"accountType\":\"SAVINGS\","
                + "\"bankInformation\":{\"branchName\":\"Main\",\"branchCode\":12,"
                + "\"routingNumber\":34,\"branchAddress\":" + address() + "}}";
    }

    String accountWithoutNumber(String balance) {
        return "{\"accountBalance\":" + balance
                + ",\"accountStatus\":\"ACTIVE\",\"accountType\":\"SAVINGS\","
                + "\"bankInformation\":{\"branchName\":\"Main\",\"branchCode\":12,"
                + "\"routingNumber\":34,\"branchAddress\":" + address() + "}}";
    }

    String accountWithoutBankInformation(long accountNumber) {
        return "{\"accountNumber\":" + accountNumber
                + ",\"accountBalance\":10,\"accountStatus\":\"ACTIVE\",\"accountType\":\"SAVINGS\"}";
    }

    String accountWithoutBranchAddress(long accountNumber) {
        return "{\"accountNumber\":" + accountNumber + ",\"accountBalance\":10,"
                + "\"accountStatus\":\"ACTIVE\",\"accountType\":\"SAVINGS\","
                + "\"bankInformation\":{\"branchName\":\"Main\",\"branchCode\":12,\"routingNumber\":34}}";
    }

    void createCustomer(long customerNumber) {
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/customers/add", customer(customerNumber));
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isEqualTo("New Customer created successfully.");
    }

    ResponseEntity<String> createV1Account(long customerNumber, long accountNumber, String balance) {
        ResponseEntity<String> response = v1Exchange(
                HttpMethod.POST, "/accounts/add/" + customerNumber, account(accountNumber, balance));
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isEqualTo("New Account created successfully.");
        return response;
    }

    long xrefCount(long customerNumber, long accountNumber) {
        long count = 0;
        for (CustomerAccountXRef xref : xrefRepository.findAll()) {
            if (Long.valueOf(customerNumber).equals(xref.getCustomerNumber())
                    && Long.valueOf(accountNumber).equals(xref.getAccountNumber())) {
                count++;
            }
        }
        return count;
    }

    long xrefCountForCustomer(long customerNumber) {
        long count = 0;
        for (CustomerAccountXRef xref : xrefRepository.findAll()) {
            if (Long.valueOf(customerNumber).equals(xref.getCustomerNumber())) {
                count++;
            }
        }
        return count;
    }
}
