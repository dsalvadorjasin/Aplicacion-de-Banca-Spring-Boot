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

class AddCustomerV2Test extends CustomerWritesTestSupport {

	@Test
	void createPersistsTheSameCustomerAndReturnsIt() throws Exception {
		long v1Number = uniqueNumber();
		long v2Number = uniqueNumber();
		Map<String, Object> v1Payload = customerPayload(v1Number, "John", "Doe");
		Map<String, Object> v2Payload = customerPayload(v2Number, "John", "Doe");

		ResponseEntity<String> v1Create = postJson("/customers/add", v1Payload);
		ResponseEntity<String> v2Create = postJson("/v2/customers/add", v2Payload);
		spec("customer-writes").assertValid(HttpMethod.POST, "/v2/customers/add",
				requestJson(v2Payload), v2Create);

		assertThat(v1Create.getStatusCode().value()).isEqualTo(201);
		assertThat(rowCount("customer")).isEqualTo(2);
		compare().v1(HttpMethod.GET, "/customers/" + v1Number)
				.v2(HttpMethod.GET, "/customers/" + v2Number)
				.ignoringFields("customerNumber")
				.assertSameJson();
		ResponseEntity<String> persistedV1Customer = get("/customers/" + v2Number);
		JsonComparison.assertEquivalent(persistedV1Customer.getBody(), v2Create.getBody(), List.of(), List.of());
	}

	@Test
	void createReturnsJsonLocationAndTheCustomer() throws Exception {
		long v1Number = uniqueNumber();
		long v2Number = uniqueNumber();
		Map<String, Object> v1Payload = customerPayload(v1Number, "John", "Doe");
		Map<String, Object> v2Payload = customerPayload(v2Number, "John", "Doe");

		ResponseEntity<String> v1 = postJson("/customers/add", v1Payload);
		ResponseEntity<String> v2 = postJson("/v2/customers/add", v2Payload);
		spec("customer-writes").assertValid(HttpMethod.POST, "/v2/customers/add", requestJson(v2Payload), v2);

		assertThat(v1.getStatusCode().value()).isEqualTo(201);
		assertThat(v1.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_PLAIN)).isTrue();
		assertThat(v1.getBody()).isEqualTo("New Customer created successfully.");
		assertThat(v1.getHeaders().getLocation()).isNull();
		assertThat(v2.getStatusCode().value()).isEqualTo(201);
		assertThat(v2.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
		assertThat(json(v2).path("customerNumber").asLong()).isEqualTo(v2Number);
		assertThat(v2.getHeaders().getLocation()).isNotNull();
		assertThat(v2.getHeaders().getLocation().isAbsolute()).isTrue();
		assertThat(v2.getHeaders().getLocation().toString())
				.endsWith("/bank-api/v2/customers/" + v2Number);
	}

	@Test
	void duplicateCreateIsRejectedWithoutChangingRowsButV1AllowsIt() throws Exception {
		long customerNumber = uniqueNumber();
		Map<String, Object> payload = customerPayload(customerNumber, "John", "Doe");
		ResponseEntity<String> first = postJson("/v2/customers/add", payload);
		spec("customer-writes").assertValid(HttpMethod.POST, "/v2/customers/add", requestJson(payload), first);
		assertThat(first.getStatusCode().value()).isEqualTo(201);

		ResponseEntity<String> duplicateV2 = postJson("/v2/customers/add", payload);
		spec("customer-writes").assertValid(HttpMethod.POST, "/v2/customers/add",
				requestJson(payload), duplicateV2);
		assertProblem(duplicateV2, 409);
		assertThat(rowCount("customer")).isEqualTo(1);

		ResponseEntity<String> duplicateV1 = postJson("/customers/add", payload);
		assertThat(duplicateV1.getStatusCode().value()).isEqualTo(201);
		assertThat(rowCount("customer")).isEqualTo(2);
	}

	@ParameterizedTest
	@ValueSource(strings = { "contactDetails", "customerAddress" })
	void requiredNestedDetailsAreRejectedOnV2BeforeV1ReturnsItsDefaultError(String missingField) throws Exception {
		long customerNumber = uniqueNumber();
		Map<String, Object> v2Payload = customerPayload(customerNumber, "John", "Doe");
		v2Payload.remove(missingField);

		ResponseEntity<String> v2 = postJson("/v2/customers/add", v2Payload);
		assertResponseConformsToSpec(HttpMethod.POST, "/v2/customers/add", requestJson(v2Payload), v2);
		assertProblem(v2, 400);
		assertThat(rowCount("customer")).isZero();

		Map<String, Object> v1Payload = customerPayload(customerNumber, "John", "Doe");
		v1Payload.remove(missingField);
		ResponseEntity<String> v1 = postJson("/customers/add", v1Payload);
		assertThat(v1.getStatusCode().value()).isEqualTo(500);
		assertThat(rowCount("customer")).isZero();
	}

	@Test
	void missingCustomerNumberIsRejectedOnV2ButAcceptedByV1() throws Exception {
		long customerNumber = uniqueNumber();
		Map<String, Object> v2Payload = customerPayload(customerNumber, "John", "Doe");
		v2Payload.remove("customerNumber");

		ResponseEntity<String> v2 = postJson("/v2/customers/add", v2Payload);
		assertResponseConformsToSpec(HttpMethod.POST, "/v2/customers/add", requestJson(v2Payload), v2);
		assertProblem(v2, 400);
		assertThat(rowCount("customer")).isZero();

		Map<String, Object> v1Payload = customerPayload(customerNumber, "John", "Doe");
		v1Payload.remove("customerNumber");
		ResponseEntity<String> v1 = postJson("/customers/add", v1Payload);
		assertThat(v1.getStatusCode().value()).isEqualTo(201);
		assertThat(rowCount("customer")).isEqualTo(1);
	}

	@Test
	void missingAndMalformedBodiesReturnProblemDetailsOnV2() throws Exception {
		ResponseEntity<String> missingV2 = exchangeJson(HttpMethod.POST, "/v2/customers/add", null);
		assertResponseConformsToSpec(HttpMethod.POST, "/v2/customers/add", null, missingV2);
		assertProblem(missingV2, 400);

		ResponseEntity<String> missingV1 = exchangeJson(HttpMethod.POST, "/customers/add", null);
		assertV1DefaultErrorJson(missingV1, 400);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<String> malformedV2 = rest.exchange("/v2/customers/add", HttpMethod.POST,
				new HttpEntity<>("{not-json", headers), String.class);
		assertResponseConformsToSpec(HttpMethod.POST, "/v2/customers/add", "{not-json", malformedV2);
		assertProblem(malformedV2, 400);
	}

	@Test
	void nonJsonContentTypeReturnsProblemDetailOnV2AndDefault415OnV1() {
		ResponseEntity<String> v2 = exchangeText(HttpMethod.POST, "/v2/customers/add", "not json");
		assertResponseConformsToSpec(HttpMethod.POST, "/v2/customers/add", null, v2);
		assertProblem(v2, 415);

		ResponseEntity<String> v1 = exchangeText(HttpMethod.POST, "/customers/add", "not json");
		assertV1DefaultErrorJson(v1, 415);
	}
}
