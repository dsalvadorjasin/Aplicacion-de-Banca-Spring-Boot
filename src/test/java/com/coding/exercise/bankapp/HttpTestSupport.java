package com.coding.exercise.bankapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.assertThat;

abstract class HttpTestSupport {
    private static final AtomicLong IDS = new AtomicLong(100000);
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;

    long id() { return IDS.incrementAndGet(); }
    ResponseEntity<String> request(HttpMethod method, String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }
    ResponseEntity<String> get(String path) { return request(HttpMethod.GET, path, null); }
    JsonNode json(ResponseEntity<String> response) throws Exception { return mapper.readTree(response.getBody()); }
    void text(ResponseEntity<String> response, int status, String body) {
        assertThat(response.getStatusCodeValue()).isEqualTo(status);
        assertThat(response.getBody()).isEqualTo(body);
    }
    void error(ResponseEntity<String> response, int status, String path) throws Exception {
        assertThat(response.getStatusCodeValue()).isEqualTo(status);
        JsonNode body = json(response);
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.get("error").asText()).isEqualTo(HttpStatus.valueOf(status).getReasonPhrase());
        assertThat(body.get("path").asText()).isEqualTo("/bank-api" + path);
        assertThat(body.hasNonNull("timestamp")).isTrue();
        assertThat(body.has("trace")).isFalse();
    }
    String address() { return "{\"address1\":\"1 Main\",\"address2\":\"Suite 2\",\"city\":\"Boston\",\"state\":\"MA\",\"zip\":\"02101\",\"country\":\"US\"}"; }
    String customer(long n) {
        return "{\"customerNumber\":" + n + ",\"firstName\":\"Ada\",\"middleName\":\"M\",\"lastName\":\"Lovelace\",\"status\":\"ACTIVE\",\"customerAddress\":" + address() + ",\"contactDetails\":{\"emailId\":\"ada@example.test\",\"homePhone\":\"123\",\"workPhone\":\"456\"}}";
    }
    void createCustomer(long n) { text(request(HttpMethod.POST, "/customers/add", customer(n)), 201, "New Customer created successfully."); }
    String account(long n, double balance) {
        return "{\"accountNumber\":" + n + ",\"accountBalance\":" + balance + ",\"accountStatus\":\"ACTIVE\",\"accountType\":\"SAVINGS\",\"bankInformation\":{\"branchName\":\"Main\",\"branchCode\":12,\"routingNumber\":34,\"branchAddress\":" + address() + "}}";
    }
    void createAccount(long customer, long n, double balance) {
        text(request(HttpMethod.POST, "/accounts/add/" + customer, account(n, balance)), 201, "New Account created successfully.");
    }
    ResponseEntity<String> transfer(long c, long from, long to, double amount) {
        return request(HttpMethod.PUT, "/accounts/transfer/" + c, "{\"fromAccountNumber\":" + from + ",\"toAccountNumber\":" + to + ",\"transferAmount\":" + amount + "}");
    }
}
