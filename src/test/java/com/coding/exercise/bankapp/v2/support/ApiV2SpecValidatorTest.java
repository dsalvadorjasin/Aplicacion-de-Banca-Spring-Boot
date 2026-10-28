package com.coding.exercise.bankapp.v2.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class ApiV2SpecValidatorTest {

	private final ApiV2SpecValidator validator = ApiV2SpecValidator.forGroup("_template");

	@Test
	void acceptsAValidExampleResponse() {
		validator.assertValid(HttpMethod.GET, "/v2/examples/12", null,
				response(HttpStatus.OK, MediaType.APPLICATION_JSON,
						"{\"exampleId\":12,\"name\":\"Example\"}"));
	}

	@Test
	void rejectsWrongTypesMissingRequiredFieldsUnknownPathsAndStatuses() {
		assertReadableFailure("/v2/examples/12",
				response(HttpStatus.OK, MediaType.APPLICATION_JSON,
						"{\"exampleId\":\"12\",\"name\":\"Example\"}"));
		assertReadableFailure("/v2/examples/12",
				response(HttpStatus.OK, MediaType.APPLICATION_JSON,
						"{\"exampleId\":12}"));
		assertReadableFailure("/v2/unknown/12",
				response(HttpStatus.OK, MediaType.APPLICATION_JSON,
						"{\"exampleId\":12,\"name\":\"Example\"}"));
		assertReadableFailure("/v2/examples/12",
				response(HttpStatus.INTERNAL_SERVER_ERROR, MediaType.APPLICATION_JSON,
						"{\"exampleId\":12,\"name\":\"Example\"}"));
	}

	@Test
	void acceptsAProblemDetailForNotFound() {
		validator.assertValid(HttpMethod.GET, "/bank-api/v2/examples/12", null,
				response(HttpStatus.NOT_FOUND, MediaType.APPLICATION_PROBLEM_JSON,
						"""
						{"type":"about:blank","title":"Not Found","status":404,
						 "detail":"Example not found","instance":"/v2/examples/12"}
						"""));
	}

	@Test
	void reportsWhenTheSpecificationIsMissing() {
		assertThatThrownBy(() -> ApiV2SpecValidator.forGroup("missing-group"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("openapi/v2/missing-group.yaml");
	}

	private void assertReadableFailure(String path, ResponseEntity<String> response) {
		assertThatThrownBy(() -> validator.assertValid(HttpMethod.GET, path, null, response))
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("OpenAPI validation failed");
	}

	private static ResponseEntity<String> response(HttpStatus status, MediaType contentType, String body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(contentType);
		return new ResponseEntity<>(body, headers, status);
	}
}
