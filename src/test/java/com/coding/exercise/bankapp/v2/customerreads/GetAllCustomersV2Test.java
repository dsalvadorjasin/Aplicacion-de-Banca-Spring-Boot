package com.coding.exercise.bankapp.v2.customerreads;

import com.coding.exercise.bankapp.model.Customer;
import com.coding.exercise.bankapp.v2.support.ApiV2Comparison;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GetAllCustomersV2Test extends CustomerReadsV2TestSupport {

    @Test
    void allCustomersMatchV1() throws Exception {
        long first = uniqueNumber();
        long second = uniqueNumber();
        createCustomerViaV1(first);
        createCustomerViaV1(second);

        ApiV2Comparison.Responses responses = ApiV2Comparison.compare(
                http, HttpMethod.GET, "/customers/all", "/v2/customers/all", null);

        assertThat(responses.v1().getStatusCode().value()).isEqualTo(200);
        assertThat(responses.v2().getStatusCode().value()).isEqualTo(200);
        assertJsonContentType(responses.v1());
        assertJsonContentType(responses.v2());
        JsonNode customers = json(responses.v2());
        assertThat(customers.isArray()).isTrue();
        assertThat(entriesWithNumber(customers, first)).hasSize(1);
        assertThat(entriesWithNumber(customers, second)).hasSize(1);
        SPEC.assertValid(HttpMethod.GET, "/v2/customers/all", null, responses.v2());
    }

    @Test
    void duplicateStoredCustomerNumbersAreListedLikeV1() throws Exception {
        long n = uniqueNumber();
        createCustomerViaV1(n);
        createCustomerViaV1(n);

        ApiV2Comparison.Responses responses = ApiV2Comparison.compare(
                http, HttpMethod.GET, "/customers/all", "/v2/customers/all", null);

        assertThat(responses.v2().getStatusCode().value()).isEqualTo(200);
        assertThat(entriesWithNumber(json(responses.v1()), n)).hasSize(2);
        assertThat(entriesWithNumber(json(responses.v2()), n)).hasSize(2);
        SPEC.assertValid(HttpMethod.GET, "/v2/customers/all", null, responses.v2());
    }

    @Test
    void storedCustomerWithoutAddressOrContactIsListedInsteadOfV1ServerError() throws Exception {
        long complete = uniqueNumber();
        createCustomerViaV1(complete);
        long n = uniqueNumber();
        Customer stored = storeCustomerWithoutAddressOrContact(n);
        try {
            assertV1BootError(get("/customers/all"), 500, "/customers/all");

            ResponseEntity<String> v2 = get("/v2/customers/all");
            assertThat(v2.getStatusCode().value()).isEqualTo(200);
            assertJsonContentType(v2);
            JsonNode customers = json(v2);
            JsonNode incomplete = entriesWithNumber(customers, n);
            assertThat(incomplete).hasSize(1);
            assertThat(incomplete.get(0).get("firstName").asText()).isEqualTo("Grace");
            assertThat(incomplete.get(0).get("customerAddress").isNull()).isTrue();
            assertThat(incomplete.get(0).get("contactDetails").isNull()).isTrue();
            assertThat(entriesWithNumber(customers, complete).get(0).at("/customerAddress/city").asText())
                    .isEqualTo("Boston");
            SPEC.assertValid(HttpMethod.GET, "/v2/customers/all", null, v2);
        } finally {
            customerRepository.delete(stored);
        }

        ApiV2Comparison.Responses afterCleanup = ApiV2Comparison.compare(
                http, HttpMethod.GET, "/customers/all", "/v2/customers/all", null);
        assertThat(afterCleanup.v1().getStatusCode().value()).isEqualTo(200);
    }
}
