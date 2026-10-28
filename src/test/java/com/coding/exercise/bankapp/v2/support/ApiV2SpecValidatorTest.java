package com.coding.exercise.bankapp.v2.support;

import java.net.URI;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiV2SpecValidatorTest {
    private static ApiV2SpecValidator validator;

    @BeforeAll
    static void loadSpec() {
        validator = ApiV2SpecValidator.forSpec("openapi/v2/_template.yaml");
    }

    @Test
    void validGetResponsePasses() {
        validator.assertValid(HttpMethod.GET, "/v2/things/7", null, response(200, "application/json",
                "{\"thingNumber\":7,\"name\":\"gear\"}"));
    }

    @Test
    void invalidBodyTypeFails() {
        assertThatThrownBy(() -> validator.assertValid(
                HttpMethod.GET, "/v2/things/7", null,
                response(200, "application/json", "{\"thingNumber\":\"seven\",\"name\":4}")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("does not conform")
                .hasMessageContaining("thingNumber");
    }

    @Test
    void problemDetailResponsePasses() {
        validator.assertValid(HttpMethod.GET, "/v2/things/7", null, response(
                404, "application/problem+json",
                "{\"type\":\"about:blank\",\"title\":\"Not Found\",\"status\":404,"
                        + "\"detail\":\"No thing\",\"instance\":\"/v2/things/7\"}"));
    }

    @Test
    void createdResponseRequiresLocation() {
        assertThatThrownBy(() -> validator.assertValid(
                HttpMethod.POST, "/v2/things", "{\"thingNumber\":7,\"name\":\"gear\"}",
                response(201, "application/json", "{\"thingNumber\":7,\"name\":\"gear\"}", false)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Location");
    }

    @Test
    void responseOnlyModeDoesNotValidateTheRequest() {
        ResponseEntity<String> response = response(400, "application/problem+json",
                "{\"type\":\"about:blank\",\"title\":\"Bad Request\",\"status\":400,"
                        + "\"detail\":\"Invalid number\",\"instance\":\"/v2/things/abc\"}");
        validator.assertValidResponse(HttpMethod.GET, "/v2/things/abc", response);
        assertThatThrownBy(() -> validator.assertValid(HttpMethod.GET, "/v2/things/abc", null, response))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("does not conform");
    }

    private static ResponseEntity<String> response(int status, String contentType, String body) {
        return response(status, contentType, body, true);
    }

    private static ResponseEntity<String> response(int status, String contentType, String body, boolean location) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        if (status == 201 && location) {
            headers.setLocation(URI.create("http://localhost:8989/bank-api/v2/things/7"));
        }
        return new ResponseEntity<>(body, headers, status);
    }
}
