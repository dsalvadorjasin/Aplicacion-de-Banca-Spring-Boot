package com.coding.exercise.bankapp.v2.customerwrites;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.coding.exercise.bankapp.v2.support.ApiV2Comparison;

class AddCustomerV2Test extends CustomerWritesV2TestSupport {

    private static final String V1 = "/customers/add";
    private static final String V2 = "/v2/customers/add";

    @Test
    void createsTheSameCustomerAsV1AndReturnsItWithLocation() throws Exception {
        long v1Number = nextNumber();
        long v2Number = nextNumber();

        ResponseEntity<String> v1 = send(HttpMethod.POST, V1, customer(v1Number, "Ada"));
        String v2Request = customer(v2Number, "Ada");
        ResponseEntity<String> v2 = send(HttpMethod.POST, V2, v2Request);

        assertV1Text(v1, HttpStatus.CREATED, "New Customer created successfully.");
        assertThat(v2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(v2.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        assertThat(v2.getHeaders().getLocation())
                .isEqualTo(URI.create("http://localhost:" + port + "/bank-api/v2/customers/" + v2Number));
        assertThat(json(v2)).isEqualTo(mapper.readTree(v2Request));
        SPEC.assertValid(HttpMethod.POST, V2, v2Request, v2);

        ApiV2Comparison.assertJsonEquals(v1GetBody(v1Number), v1GetBody(v2Number), "/customerNumber");
        ApiV2Comparison.assertJsonEquals(v1GetBody(v2Number), v2.getBody());
        assertThat(storedCount(v1Number)).isEqualTo(1);
        assertThat(storedCount(v2Number)).isEqualTo(1);
        assertSameSecurityHeaders(v1, v2);
    }

    @Test
    void customerWithOnlyRequiredFieldsMatchesV1() throws Exception {
        long v1Number = nextNumber();
        long v2Number = nextNumber();
        String minimal = "{\"customerNumber\":%d,\"customerAddress\":{},\"contactDetails\":{}}";

        ResponseEntity<String> v1 = send(HttpMethod.POST, V1, minimal.formatted(v1Number));
        ResponseEntity<String> v2 = send(HttpMethod.POST, V2, minimal.formatted(v2Number));

        assertV1Text(v1, HttpStatus.CREATED, "New Customer created successfully.");
        assertThat(v2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        SPEC.assertValid(HttpMethod.POST, V2, minimal.formatted(v2Number), v2);
        ApiV2Comparison.assertJsonEquals(v1GetBody(v1Number), v1GetBody(v2Number), "/customerNumber");
        ApiV2Comparison.assertJsonEquals(v1GetBody(v2Number), v2.getBody());
    }

    @Test
    void existingCustomerNumberIsAConflictInV2ButADuplicateInV1() throws Exception {
        long number = nextNumber();
        createWithV1(number, "Ada");

        ResponseEntity<String> v2 = send(HttpMethod.POST, V2, customer(number, "Grace"));
        assertV2Problem(v2, HttpStatus.CONFLICT, V2);
        SPEC.assertValid(HttpMethod.POST, V2, customer(number, "Grace"), v2);
        assertThat(storedCount(number)).isEqualTo(1);
        assertThat(json(v1Get(number)).get("firstName").asText()).isEqualTo("Ada");

        ResponseEntity<String> v1 = send(HttpMethod.POST, V1, customer(number, "Grace"));
        assertV1Text(v1, HttpStatus.CREATED, "New Customer created successfully.");
        assertThat(storedCount(number)).isEqualTo(2);
    }

    @Test
    void alreadyDuplicatedStoredNumberIsAConflictAndCreatesNothing() throws Exception {
        long number = nextNumber();
        createWithV1(number, "Ada");
        createWithV1(number, "Ada");

        String marker = "Duplicate-" + number;
        ResponseEntity<String> v2 = send(HttpMethod.POST, V2, customer(number, marker));

        assertV2Problem(v2, HttpStatus.CONFLICT, V2);
        SPEC.assertValid(HttpMethod.POST, V2, customer(number, marker), v2);
        assertThat(storedCount(number)).isEqualTo(2);
        assertThat(storedCountByFirstName(marker)).isZero();
    }

    @Test
    void missingCustomerNumberIsRejectedByV2ButStoredByV1() throws Exception {
        String v2Marker = "NoNumberV2-" + nextNumber();
        String v1Marker = "NoNumberV1-" + nextNumber();

        ResponseEntity<String> v2 = send(HttpMethod.POST, V2, customerWithoutNumber(v2Marker));
        assertV2Problem(v2, HttpStatus.BAD_REQUEST, V2);
        SPEC.assertValidResponse(HttpMethod.POST, V2, v2);
        assertThat(storedCountByFirstName(v2Marker)).isZero();

        ResponseEntity<String> v1 = send(HttpMethod.POST, V1, customerWithoutNumber(v1Marker));
        assertV1Text(v1, HttpStatus.CREATED, "New Customer created successfully.");
        assertThat(storedCountByFirstName(v1Marker)).isEqualTo(1);
    }

    @Test
    void missingAddressOrContactIsBadRequestInV2AndServerErrorInV1() throws Exception {
        for (String field : new String[] {"customerAddress", "contactDetails"}) {
            long number = nextNumber();
            String body = withoutField(customer(number, "Ada"), field);

            ResponseEntity<String> v1 = send(HttpMethod.POST, V1, body);
            ResponseEntity<String> v2 = send(HttpMethod.POST, V2, body);

            assertV1BootError(v1, HttpStatus.INTERNAL_SERVER_ERROR, V1);
            assertV2Problem(v2, HttpStatus.BAD_REQUEST, V2);
            assertThat(json(v2).get("detail").asText()).contains(field);
            SPEC.assertValidResponse(HttpMethod.POST, V2, v2);
            assertThat(storedCount(number)).isZero();
        }
    }

    @Test
    void malformedBodiesAreProblemDetailsInV2AndBootErrorsInV1() throws Exception {
        String[] invalid = {"{", "", "[]", "null", "{\"customerNumber\":\"not-a-number\"}",
                "{\"customerNumber\":99999999999999999999,\"customerAddress\":{},\"contactDetails\":{}}",
                "{\"customerNumber\":1,\"customerAddress\":\"x\",\"contactDetails\":{}}"};
        for (String body : invalid) {
            ResponseEntity<String> v1 = send(HttpMethod.POST, V1, body);
            ResponseEntity<String> v2 = send(HttpMethod.POST, V2, body);

            assertV1BootError(v1, HttpStatus.BAD_REQUEST, V1);
            assertV2Problem(v2, HttpStatus.BAD_REQUEST, V2);
            SPEC.assertValidResponse(HttpMethod.POST, V2, v2);
        }
    }

    @Test
    void unsupportedContentTypeIsAProblemDetailInV2() throws Exception {
        long number = nextNumber();
        ResponseEntity<String> v1 = sendText(HttpMethod.POST, V1, customer(number, "Ada"));
        ResponseEntity<String> v2 = sendText(HttpMethod.POST, V2, customer(number, "Ada"));

        assertV1BootError(v1, HttpStatus.UNSUPPORTED_MEDIA_TYPE, V1);
        assertV2Problem(v2, HttpStatus.UNSUPPORTED_MEDIA_TYPE, V2);
        SPEC.assertValidResponse(HttpMethod.POST, V2, v2);
        assertThat(storedCount(number)).isZero();
    }
}
