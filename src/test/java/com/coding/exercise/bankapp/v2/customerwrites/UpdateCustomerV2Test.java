package com.coding.exercise.bankapp.v2.customerwrites;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.coding.exercise.bankapp.v2.support.ApiV2Comparison;

class UpdateCustomerV2Test extends CustomerWritesV2TestSupport {

    private String updated(long number) {
        return customer(number, "Grace").replace("Boston", "Cambridge").replace("\"123\"", "\"789\"")
                .replace("ACTIVE", "INACTIVE");
    }

    @Test
    void appliesTheSameUpdateAsV1AndReturnsTheUpdatedCustomer() throws Exception {
        long v1Number = nextNumber();
        long v2Number = nextNumber();
        createWithV1(v1Number, "Ada");
        createWithV1(v2Number, "Ada");

        ResponseEntity<String> v1 = send(HttpMethod.PUT, "/customers/" + v1Number, updated(v1Number));
        ResponseEntity<String> v2 = send(HttpMethod.PUT, "/v2/customers/" + v2Number, updated(v2Number));

        assertV1Text(v1, HttpStatus.OK, "Success: Customer updated.");
        assertThat(v2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(v2.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        assertThat(json(v2)).isEqualTo(mapper.readTree(updated(v2Number)));
        SPEC.assertValid(HttpMethod.PUT, "/v2/customers/" + v2Number, updated(v2Number), v2);

        ApiV2Comparison.assertJsonEquals(v1GetBody(v1Number), v1GetBody(v2Number), "/customerNumber");
        ApiV2Comparison.assertJsonEquals(v1GetBody(v2Number), v2.getBody());
        assertSameSecurityHeaders(v1, v2);
    }

    @Test
    void omittedOptionalFieldsAreClearedAndBodyNumberIsIgnoredLikeV1() throws Exception {
        long v1Number = nextNumber();
        long v2Number = nextNumber();
        createWithV1(v1Number, "Ada");
        createWithV1(v2Number, "Ada");
        String partial = "{\"customerNumber\":%d,\"firstName\":\"Only\",\"customerAddress\":{\"city\":\"Paris\"},"
                + "\"contactDetails\":{\"emailId\":\"only@example.test\"}}";
        long otherNumber = nextNumber();

        ResponseEntity<String> v1 = send(HttpMethod.PUT, "/customers/" + v1Number, partial.formatted(otherNumber));
        ResponseEntity<String> v2 = send(HttpMethod.PUT, "/v2/customers/" + v2Number, partial.formatted(otherNumber));

        assertV1Text(v1, HttpStatus.OK, "Success: Customer updated.");
        assertThat(v2.getStatusCode()).isEqualTo(HttpStatus.OK);
        SPEC.assertValid(HttpMethod.PUT, "/v2/customers/" + v2Number, partial.formatted(otherNumber), v2);
        assertThat(json(v2).get("customerNumber").asLong()).isEqualTo(v2Number);
        assertThat(json(v2).get("lastName").isNull()).isTrue();
        assertThat(storedCount(otherNumber)).isZero();
        ApiV2Comparison.assertJsonEquals(v1GetBody(v1Number), v1GetBody(v2Number), "/customerNumber");
        ApiV2Comparison.assertJsonEquals(v1GetBody(v2Number), v2.getBody());
    }

    @Test
    void missingCustomerIsAProblemDetailInV2AndPlainTextInV1() throws Exception {
        long number = nextNumber();

        ResponseEntity<String> v1 = send(HttpMethod.PUT, "/customers/" + number, updated(number));
        ResponseEntity<String> v2 = send(HttpMethod.PUT, "/v2/customers/" + number, updated(number));

        assertV1Text(v1, HttpStatus.NOT_FOUND, "Customer Number " + number + " not found.");
        assertV2Problem(v2, HttpStatus.NOT_FOUND, "/v2/customers/" + number);
        SPEC.assertValid(HttpMethod.PUT, "/v2/customers/" + number, updated(number), v2);
        assertThat(storedCount(number)).isZero();
    }

    @Test
    void missingAddressOrContactIsBadRequestInV2AndServerErrorInV1WithoutChanges() throws Exception {
        long number = nextNumber();
        createWithV1(number, "Ada");
        String before = v1GetBody(number);
        for (String field : new String[] {"customerAddress", "contactDetails"}) {
            String body = withoutField(updated(number), field);

            ResponseEntity<String> v1 = send(HttpMethod.PUT, "/customers/" + number, body);
            ResponseEntity<String> v2 = send(HttpMethod.PUT, "/v2/customers/" + number, body);

            assertV1BootError(v1, HttpStatus.INTERNAL_SERVER_ERROR, "/customers/" + number);
            assertV2Problem(v2, HttpStatus.BAD_REQUEST, "/v2/customers/" + number);
            assertThat(json(v2).get("detail").asText()).contains(field);
            SPEC.assertValidResponse(HttpMethod.PUT, "/v2/customers/" + number, v2);
            ApiV2Comparison.assertJsonEquals(before, v1GetBody(number));
        }
    }

    @Test
    void textLongerThanTheColumnIsBadRequestInV2AndServerErrorInV1WithoutChanges() throws Exception {
        long number = nextNumber();
        createWithV1(number, "Ada");
        String before = v1GetBody(number);
        String body = updated(number).replace("Cambridge", "z".repeat(256));
        String path = "/customers/" + number;

        ResponseEntity<String> v2 = send(HttpMethod.PUT, "/v2" + path, body);
        ApiV2Comparison.assertJsonEquals(before, v1GetBody(number));
        ResponseEntity<String> v1 = send(HttpMethod.PUT, path, body);

        assertV2Problem(v2, HttpStatus.BAD_REQUEST, "/v2" + path);
        assertThat(json(v2).get("detail").asText()).contains("customerAddress.city").contains("255");
        SPEC.assertValidResponse(HttpMethod.PUT, "/v2" + path, v2);
        assertV1BootError(v1, HttpStatus.INTERNAL_SERVER_ERROR, path);
    }

    @Test
    void duplicateStoredNumberIsAConflictInV2AndServerErrorInV1WithoutChanges() throws Exception {
        long number = nextNumber();
        createWithV1(number, "Ada");
        createWithV1(number, "Ada");

        ResponseEntity<String> v2 = send(HttpMethod.PUT, "/v2/customers/" + number, updated(number));
        ResponseEntity<String> v1 = send(HttpMethod.PUT, "/customers/" + number, updated(number));

        assertV2Problem(v2, HttpStatus.CONFLICT, "/v2/customers/" + number);
        SPEC.assertValid(HttpMethod.PUT, "/v2/customers/" + number, updated(number), v2);
        assertV1BootError(v1, HttpStatus.INTERNAL_SERVER_ERROR, "/customers/" + number);
        assertThat(jdbc.queryForList("select first_name from customer where customer_number = ?", String.class,
                number)).containsExactly("Ada", "Ada");
    }

    @Test
    void malformedBodiesAndCustomerNumbersAreProblemDetailsInV2AndBootErrorsInV1() throws Exception {
        long number = nextNumber();
        createWithV1(number, "Ada");
        String before = v1GetBody(number);
        for (String body : new String[] {"{", "", "[]", "null", "{\"customerNumber\":\"not-a-number\"}"}) {
            ResponseEntity<String> v1 = send(HttpMethod.PUT, "/customers/" + number, body);
            ResponseEntity<String> v2 = send(HttpMethod.PUT, "/v2/customers/" + number, body);

            assertV1BootError(v1, HttpStatus.BAD_REQUEST, "/customers/" + number);
            assertV2Problem(v2, HttpStatus.BAD_REQUEST, "/v2/customers/" + number);
            SPEC.assertValidResponse(HttpMethod.PUT, "/v2/customers/" + number, v2);
        }
        for (String badNumber : new String[] {"nope", "1.5", "99999999999999999999"}) {
            ResponseEntity<String> v1 = send(HttpMethod.PUT, "/customers/" + badNumber, updated(number));
            ResponseEntity<String> v2 = send(HttpMethod.PUT, "/v2/customers/" + badNumber, updated(number));

            assertV1BootError(v1, HttpStatus.BAD_REQUEST, "/customers/" + badNumber);
            assertV2Problem(v2, HttpStatus.BAD_REQUEST, "/v2/customers/" + badNumber);
            SPEC.assertValidResponse(HttpMethod.PUT, "/v2/customers/" + badNumber, v2);
        }
        ApiV2Comparison.assertJsonEquals(before, v1GetBody(number));
    }

    @Test
    void unsupportedContentTypeIsAProblemDetailInV2() throws Exception {
        long number = nextNumber();
        createWithV1(number, "Ada");

        ResponseEntity<String> v1 = sendText(HttpMethod.PUT, "/customers/" + number, updated(number));
        ResponseEntity<String> v2 = sendText(HttpMethod.PUT, "/v2/customers/" + number, updated(number));

        assertV1BootError(v1, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "/customers/" + number);
        assertV2Problem(v2, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "/v2/customers/" + number);
        SPEC.assertValidResponse(HttpMethod.PUT, "/v2/customers/" + number, v2);
        assertThat(json(v1Get(number)).get("firstName").asText()).isEqualTo("Ada");
    }
}
