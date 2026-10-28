package com.coding.exercise.bankapp.v2.customerwrites;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.atlassian.oai.validator.report.ValidationReport;
import com.coding.exercise.bankapp.v2.support.ApiV2SpecValidator;
import com.coding.exercise.bankapp.v2.support.ApiV2TestSupport;
import com.fasterxml.jackson.core.JsonProcessingException;

abstract class CustomerWritesTestSupport extends ApiV2TestSupport {

	protected String requestJson(Object request) throws JsonProcessingException {
		return objectMapper.writeValueAsString(request);
	}

	protected void assertProblem(ResponseEntity<String> response, int status) {
		assertThat(response.getStatusCode().value()).isEqualTo(status);
		MediaType contentType = response.getHeaders().getContentType();
		assertThat(contentType).isNotNull();
		assertThat(contentType.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
		assertThat(json(response).path("status").asInt()).isEqualTo(status);
	}

	protected void assertV1DefaultErrorJson(ResponseEntity<String> response, int status) {
		assertThat(response.getStatusCode().value()).isEqualTo(status);
		MediaType contentType = response.getHeaders().getContentType();
		assertThat(contentType).isNotNull();
		assertThat(contentType.isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
		assertThat(contentType.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isFalse();
		assertThat(response.getBody()).contains("\"status\":" + status);
	}

	protected void assertResponseConformsToSpec(HttpMethod method, String path, String requestBody,
			ResponseEntity<String> response) {
		ValidationReport report = spec("customer-writes").validate(method, path, requestBody, response);
		List<String> unexpected = report.getMessages().stream()
				.filter(message -> !message.getKey().startsWith("validation.request"))
				.map(message -> message.getKey() + ": " + message.getMessage())
				.toList();
		assertThat(unexpected).as("OpenAPI validation report:%n%s", report).isEmpty();
	}

	protected ResponseEntity<String> exchangeText(HttpMethod method, String path, String body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.TEXT_PLAIN);
		return rest.exchange(path, method, new HttpEntity<>(body, headers), String.class);
	}

	protected void createCustomerV1(long customerNumber) {
		ResponseEntity<String> response = postJson("/customers/add",
				customerPayload(customerNumber, "John", "Doe"));
		assertThat(response.getStatusCode().value()).isEqualTo(201);
	}

	protected void createAccountV1(long customerNumber, long accountNumber) {
		ResponseEntity<String> response = postJson("/accounts/add/" + customerNumber,
				accountPayload(accountNumber, 100.0, "Checking"));
		assertThat(response.getStatusCode().value()).isEqualTo(201);
	}
}
