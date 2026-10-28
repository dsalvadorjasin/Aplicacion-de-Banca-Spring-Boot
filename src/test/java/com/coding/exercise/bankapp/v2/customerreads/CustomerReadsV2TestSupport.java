package com.coding.exercise.bankapp.v2.customerreads;

import java.util.concurrent.atomic.AtomicLong;

import com.coding.exercise.bankapp.model.Customer;
import com.coding.exercise.bankapp.repository.CustomerRepository;
import com.coding.exercise.bankapp.v2.support.ApiV2Comparison;
import com.coding.exercise.bankapp.v2.support.ApiV2SpecValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:customer-reads-v2;DB_CLOSE_DELAY=-1"})
abstract class CustomerReadsV2TestSupport {
    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis() * 1000);
    static final ObjectMapper MAPPER = new ObjectMapper();
    static final ApiV2SpecValidator SPEC = ApiV2SpecValidator.forGroup("customer-reads");

    @Autowired TestRestTemplate http;
    @Autowired CustomerRepository customerRepository;

    static long uniqueNumber() {
        return IDS.incrementAndGet();
    }

    static String customerJson(long customerNumber) {
        return "{\"customerNumber\":" + customerNumber
                + ",\"firstName\":\"Ada\",\"middleName\":\"M\",\"lastName\":\"Lovelace\",\"status\":\"ACTIVE\","
                + "\"customerAddress\":{\"address1\":\"1 Main\",\"address2\":\"Suite 2\",\"city\":\"Boston\","
                + "\"state\":\"MA\",\"zip\":\"02101\",\"country\":\"US\"},"
                + "\"contactDetails\":{\"emailId\":\"ada@example.test\",\"homePhone\":\"123\",\"workPhone\":\"456\"}}";
    }

    void createCustomerViaV1(long customerNumber) {
        ResponseEntity<String> created = ApiV2Comparison.exchange(
                http, HttpMethod.POST, "/customers/add", customerJson(customerNumber));
        assertThat(created.getStatusCode().value()).isEqualTo(201);
    }

    /** Stores a customer V1 cannot create: no address and no contact details. Delete it when done. */
    Customer storeCustomerWithoutAddressOrContact(long customerNumber) {
        return customerRepository.save(Customer.builder()
                .customerNumber(customerNumber)
                .firstName("Grace")
                .lastName("Hopper")
                .status("ACTIVE")
                .build());
    }

    ResponseEntity<String> get(String path) {
        return ApiV2Comparison.exchange(http, HttpMethod.GET, path, null);
    }

    static JsonNode json(ResponseEntity<String> response) throws Exception {
        assertThat(response.getBody()).isNotNull();
        return MAPPER.readTree(response.getBody());
    }

    static void assertJsonContentType(ResponseEntity<String> response) {
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
    }

    static void assertV1BootError(ResponseEntity<String> response, int status, String path) throws Exception {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertJsonContentType(response);
        JsonNode body = json(response);
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.has("error")).isTrue();
        assertThat(body.has("timestamp")).isTrue();
        assertThat(body.get("path").asText()).isEqualTo("/bank-api" + path);
        assertThat(body.has("type")).isFalse();
        assertThat(body.has("instance")).isFalse();
    }

    static void assertProblem(ResponseEntity<String> response, int status, String path) throws Exception {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .isTrue();
        JsonNode body = json(response);
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.get("title").asText()).isNotBlank();
        assertThat(body.get("detail").asText()).isNotBlank();
        assertThat(body.get("instance").asText()).isEqualTo("/bank-api" + path);
        assertThat(body.has("trace")).isFalse();
        assertThat(body.has("exception")).isFalse();
    }

    static JsonNode entriesWithNumber(JsonNode customers, long customerNumber) {
        com.fasterxml.jackson.databind.node.ArrayNode matches = MAPPER.createArrayNode();
        customers.forEach(customer -> {
            if (customer.path("customerNumber").asLong() == customerNumber) {
                matches.add(customer);
            }
        });
        return matches;
    }
}
