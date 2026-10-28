package com.coding.exercise.bankapp.v2.customerwrites;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.coding.exercise.bankapp.v2.support.JsonComparison;

class UpdateCustomerV2Test extends CustomerWritesTestSupport {

	@Test
	void updatePersistsTheSameCustomerAndReturnsIt() throws Exception {
		long v1Number = uniqueNumber();
		long v2Number = uniqueNumber();
		createCustomerV1(v1Number);
		createCustomerV1(v2Number);
		Map<String, Object> v1Payload = updatePayload(v1Number);
		Map<String, Object> v2Payload = updatePayload(v2Number);

		ResponseEntity<String> v1Update = putJson("/customers/" + v1Number, v1Payload);
		ResponseEntity<String> v2Update = putJson("/v2/customers/" + v2Number, v2Payload);
		spec("customer-writes").assertValid(HttpMethod.PUT, "/v2/customers/" + v2Number,
				requestJson(v2Payload), v2Update);

		assertThat(v1Update.getStatusCode().value()).isEqualTo(200);
		assertThat(v1Update.getBody()).isEqualTo("Success: Customer updated.");
		assertThat(rowCount("customer")).isEqualTo(2);
		compare().v1(HttpMethod.GET, "/customers/" + v1Number)
				.v2(HttpMethod.GET, "/customers/" + v2Number)
				.ignoringFields("customerNumber")
				.assertSameJson();
		ResponseEntity<String> persistedV1Customer = get("/customers/" + v2Number);
		JsonComparison.assertEquivalent(persistedV1Customer.getBody(), v2Update.getBody(), List.of(), List.of());
	}

	@Test
	void updateReturnsTheCustomerAsJsonInsteadOfV1PlainText() throws Exception {
		long customerNumber = uniqueNumber();
		createCustomerV1(customerNumber);
		Map<String, Object> payload = updatePayload(customerNumber);

		ResponseEntity<String> v1 = putJson("/customers/" + customerNumber, payload);
		ResponseEntity<String> v2 = putJson("/v2/customers/" + customerNumber, payload);
		spec("customer-writes").assertValid(HttpMethod.PUT, "/v2/customers/" + customerNumber,
				requestJson(payload), v2);

		assertThat(v1.getStatusCode().value()).isEqualTo(200);
		assertThat(v1.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_PLAIN)).isTrue();
		assertThat(v1.getBody()).isEqualTo("Success: Customer updated.");
		assertThat(v2.getStatusCode().value()).isEqualTo(200);
		assertThat(v2.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
		assertThat(json(v2).path("customerNumber").asLong()).isEqualTo(customerNumber);
	}

	@Test
	void bodyCustomerNumberIsIgnoredAndThePathNumberIsKept() throws Exception {
		long v1Number = uniqueNumber();
		long v2Number = uniqueNumber();
		long v1BodyNumber = uniqueNumber();
		long v2BodyNumber = uniqueNumber();
		createCustomerV1(v1Number);
		createCustomerV1(v2Number);
		Map<String, Object> v1Payload = updatePayload(v1BodyNumber);
		Map<String, Object> v2Payload = updatePayload(v2BodyNumber);

		putJson("/customers/" + v1Number, v1Payload);
		ResponseEntity<String> v2 = putJson("/v2/customers/" + v2Number, v2Payload);
		spec("customer-writes").assertValid(HttpMethod.PUT, "/v2/customers/" + v2Number,
				requestJson(v2Payload), v2);

		assertThat(json(v2).path("customerNumber").asLong()).isEqualTo(v2Number);
		assertThat(get("/customers/" + v1Number).getStatusCode().value()).isEqualTo(200);
		assertThat(get("/customers/" + v2Number).getStatusCode().value()).isEqualTo(200);
		assertThat(get("/customers/" + v1BodyNumber).getBody()).isNullOrEmpty();
		assertThat(get("/customers/" + v2BodyNumber).getBody()).isNullOrEmpty();
	}

	@Test
	void missingCustomerReturnsProblemDetailOnV2AndV1PlainText404() throws Exception {
		long customerNumber = uniqueNumber();
		Map<String, Object> payload = updatePayload(customerNumber);

		ResponseEntity<String> v1 = putJson("/customers/" + customerNumber, payload);
		assertThat(v1.getStatusCode().value()).isEqualTo(404);
		assertThat(v1.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_PLAIN)).isTrue();
		assertThat(v1.getBody()).isEqualTo("Customer Number " + customerNumber + " not found.");

		ResponseEntity<String> v2 = putJson("/v2/customers/" + customerNumber, payload);
		spec("customer-writes").assertValid(HttpMethod.PUT, "/v2/customers/" + customerNumber,
				requestJson(payload), v2);
		assertProblem(v2, 404);
		assertThat(json(v2).path("detail").asText()).contains(Long.toString(customerNumber));
	}

	@ParameterizedTest
	@ValueSource(strings = { "contactDetails", "customerAddress" })
	void requiredNestedDetailsFailBeforeChangingTheV2Customer(String missingField) throws Exception {
		long customerNumber = uniqueNumber();
		createCustomerV1(customerNumber);
		Map<String, Object> v2Payload = updatePayload(customerNumber);
		v2Payload.remove(missingField);

		ResponseEntity<String> v2 = putJson("/v2/customers/" + customerNumber, v2Payload);
		assertResponseConformsToSpec(HttpMethod.PUT, "/v2/customers/" + customerNumber,
				requestJson(v2Payload), v2);
		assertProblem(v2, 400);
		assertThat(json(get("/customers/" + customerNumber)).path("firstName").asText()).isEqualTo("John");

		Map<String, Object> v1Payload = updatePayload(customerNumber);
		v1Payload.remove(missingField);
		ResponseEntity<String> v1 = putJson("/customers/" + customerNumber, v1Payload);
		assertThat(v1.getStatusCode().value()).isEqualTo(500);
		assertThat(json(get("/customers/" + customerNumber)).path("firstName").asText()).isEqualTo("John");
	}

	@Test
	void duplicateCustomerNumberReturnsConflictOnV2AndServerErrorOnV1() throws Exception {
		long customerNumber = uniqueNumber();
		Map<String, Object> payload = customerPayload(customerNumber, "John", "Doe");
		assertThat(postJson("/customers/add", payload).getStatusCode().value()).isEqualTo(201);
		assertThat(postJson("/customers/add", payload).getStatusCode().value()).isEqualTo(201);

		Map<String, Object> v2Payload = updatePayload(customerNumber);
		ResponseEntity<String> v2 = putJson("/v2/customers/" + customerNumber, v2Payload);
		spec("customer-writes").assertValid(HttpMethod.PUT, "/v2/customers/" + customerNumber,
				requestJson(v2Payload), v2);
		assertProblem(v2, 409);
		assertThat(jdbcTemplate.queryForList("SELECT first_name FROM customer", String.class))
				.containsExactlyInAnyOrder("John", "John");

		ResponseEntity<String> v1 = putJson("/customers/" + customerNumber,
				updatePayload(customerNumber));
		assertThat(v1.getStatusCode().value()).isEqualTo(500);
	}

	@Test
	void missingBodyAndNonNumericPathReturnV2ProblemsAndV1DefaultErrors() throws Exception {
		long customerNumber = uniqueNumber();
		Map<String, Object> validPayload = updatePayload(customerNumber);

		ResponseEntity<String> missingV2 = exchangeJson(HttpMethod.PUT,
				"/v2/customers/" + customerNumber, null);
		assertResponseConformsToSpec(HttpMethod.PUT, "/v2/customers/" + customerNumber, null, missingV2);
		assertProblem(missingV2, 400);
		assertV1DefaultErrorJson(exchangeJson(HttpMethod.PUT, "/customers/" + customerNumber, null), 400);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		String malformedBody = "{not-json";
		ResponseEntity<String> malformedV2 = rest.exchange("/v2/customers/" + customerNumber, HttpMethod.PUT,
				new HttpEntity<>(malformedBody, headers), String.class);
		assertResponseConformsToSpec(HttpMethod.PUT, "/v2/customers/" + customerNumber,
				malformedBody, malformedV2);
		assertProblem(malformedV2, 400);
		ResponseEntity<String> malformedV1 = rest.exchange("/customers/" + customerNumber, HttpMethod.PUT,
				new HttpEntity<>(malformedBody, headers), String.class);
		assertV1DefaultErrorJson(malformedV1, 400);

		String invalidPath = "/v2/customers/abc";
		ResponseEntity<String> nonNumericV2 = putJson(invalidPath, validPayload);
		assertResponseConformsToSpec(HttpMethod.PUT, invalidPath, requestJson(validPayload), nonNumericV2);
		assertProblem(nonNumericV2, 400);
		assertV1DefaultErrorJson(putJson("/customers/abc", validPayload), 400);
	}

	@Test
	void nonJsonContentTypeReturnsProblemDetailOnV2AndDefault415OnV1() {
		long customerNumber = uniqueNumber();
		ResponseEntity<String> v2 = exchangeText(HttpMethod.PUT, "/v2/customers/" + customerNumber, "not json");
		assertResponseConformsToSpec(HttpMethod.PUT, "/v2/customers/" + customerNumber, null, v2);
		assertProblem(v2, 415);

		ResponseEntity<String> v1 = exchangeText(HttpMethod.PUT, "/customers/" + customerNumber, "not json");
		assertV1DefaultErrorJson(v1, 415);
	}

	private Map<String, Object> updatePayload(long bodyCustomerNumber) {
		Map<String, Object> payload = customerPayload(bodyCustomerNumber, "Roger", "Federer");
		payload.put("middleName", "D");
		payload.put("status", "Inactive");
		payload.put("customerAddress", address("123 McKee Ave", "Chicago", "IL", "60076"));
		return payload;
	}
}
