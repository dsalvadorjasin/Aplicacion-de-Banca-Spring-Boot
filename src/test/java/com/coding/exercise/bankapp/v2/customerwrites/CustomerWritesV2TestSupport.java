package com.coding.exercise.bankapp.v2.customerwrites;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.coding.exercise.bankapp.v2.support.ApiV2Comparison;
import com.coding.exercise.bankapp.v2.support.ApiV2SpecValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:apiv2-customer-writes;DB_CLOSE_DELAY=-1"})
abstract class CustomerWritesV2TestSupport {

    static final String GROUP = "customer-writes";
    static final ApiV2SpecValidator SPEC = ApiV2SpecValidator.forGroup(GROUP);
    private static final AtomicLong NUMBERS =
            new AtomicLong(7_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L));

    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @LocalServerPort int port;

    long nextNumber() {
        return NUMBERS.addAndGet(ThreadLocalRandom.current().nextLong(1, 1_000));
    }

    String customer(long number, String firstName) {
        return customerJson("\"customerNumber\":" + number + ",", firstName);
    }

    String customerWithoutNumber(String firstName) {
        return customerJson("", firstName);
    }

    private String customerJson(String numberField, String firstName) {
        return "{" + numberField + "\"firstName\":\"" + firstName + "\",\"middleName\":\"M\",\"lastName\":\"Lovelace\","
                + "\"status\":\"ACTIVE\",\"customerAddress\":{\"address1\":\"1 Main\",\"address2\":\"Suite 2\","
                + "\"city\":\"Boston\",\"state\":\"MA\",\"zip\":\"02101\",\"country\":\"US\"},"
                + "\"contactDetails\":{\"emailId\":\"ada@example.test\",\"homePhone\":\"123\",\"workPhone\":\"456\"}}";
    }

    String withoutField(String json, String field) throws Exception {
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(json);
        node.remove(field);
        return mapper.writeValueAsString(node);
    }

    ResponseEntity<String> send(HttpMethod method, String path, String body) {
        return ApiV2Comparison.exchange(http, method, path, body);
    }

    ResponseEntity<String> sendText(HttpMethod method, String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    void createWithV1(long number, String firstName) {
        ResponseEntity<String> response = send(HttpMethod.POST, "/customers/add", customer(number, firstName));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    ResponseEntity<String> v1Get(long number) {
        return send(HttpMethod.GET, "/customers/" + number, null);
    }

    String v1GetBody(long number) {
        ResponseEntity<String> response = v1Get(number);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    int storedCount(long number) {
        return jdbc.queryForObject("select count(*) from customer where customer_number = ?", Integer.class, number);
    }

    int storedCountByFirstName(String firstName) {
        return jdbc.queryForObject("select count(*) from customer where first_name = ?", Integer.class, firstName);
    }

    JsonNode json(ResponseEntity<String> response) throws Exception {
        return mapper.readTree(response.getBody());
    }

    void assertV2Problem(ResponseEntity<String> response, HttpStatus status, String path) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .as("content type %s", response.getHeaders().getContentType()).isTrue();
        JsonNode body = json(response);
        assertThat(body.get("status").asInt()).isEqualTo(status.value());
        assertThat(body.get("title").asText()).isEqualTo(status.getReasonPhrase());
        assertThat(body.hasNonNull("detail")).isTrue();
        assertThat(body.get("instance").asText()).isEqualTo("/bank-api" + path);
        assertThat(body.has("trace")).isFalse();
    }

    void assertV1BootError(ResponseEntity<String> response, HttpStatus status, String path) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        JsonNode body = json(response);
        assertThat(body.get("status").asInt()).isEqualTo(status.value());
        assertThat(body.get("error").asText()).isEqualTo(status.getReasonPhrase());
        assertThat(body.get("path").asText()).isEqualTo("/bank-api" + path);
        assertThat(body.has("instance")).isFalse();
    }

    void assertV1Text(ResponseEntity<String> response, HttpStatus status, String body) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isEqualTo(body);
        assertThat(response.getHeaders().getLocation()).isNull();
    }

    void assertSameSecurityHeaders(ResponseEntity<String> v1, ResponseEntity<String> v2) {
        for (String header : new String[] {"X-XSS-Protection", "X-Content-Type-Options", "Cache-Control",
                "Pragma", "Expires", "X-Frame-Options", "Set-Cookie", "WWW-Authenticate"}) {
            assertThat(v2.getHeaders().get(header)).as(header).isEqualTo(v1.getHeaders().get(header));
        }
    }
}
