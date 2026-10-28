package com.coding.exercise.bankapp.v2.customerreads;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.coding.exercise.bankapp.v2.support.ApiV2TestSupport;
import com.coding.exercise.bankapp.v2.support.V1V2Comparison;
import com.fasterxml.jackson.databind.JsonNode;

class CustomerReadsV2Test extends ApiV2TestSupport {

	private static final String SPEC_GROUP = "customer-reads";
	private static final String ALL_CUSTOMERS_PATH = "/v2/customers/all";
	private static final String SPEC_VALID_CUSTOMER_PATH = "/v2/customers/0";
	private static final String[] SECURITY_HEADERS = {
			"X-Content-Type-Options", "X-Frame-Options", "Cache-Control",
			"Pragma", "Expires", "X-XSS-Protection"
	};

	@Test
	void allCustomersWithEmptyDatabaseMatchesV1() {
		V1V2Comparison.Result result = compare()
				.v1(HttpMethod.GET, "/customers/all")
				.v2(HttpMethod.GET, ALL_CUSTOMERS_PATH)
				.assertSameJson();

		assertThat(result.v1().getStatusCode().value()).isEqualTo(200);
		assertThat(result.v2().getStatusCode().value()).isEqualTo(200);
		assertThat(result.v1().getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
		assertThat(result.v2().getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
		assertThat(json(result.v1())).isEmpty();
		assertThat(json(result.v2())).isEmpty();
		spec(SPEC_GROUP).assertValid(HttpMethod.GET, ALL_CUSTOMERS_PATH, null, result.v2());
	}

	@Test
	void allCustomersWithTwoCustomersMatchesV1() {
		long firstNumber = uniqueNumber();
		long secondNumber = uniqueNumber();
		createCustomer(firstNumber, "John", "Doe");
		createCustomer(secondNumber, "Jane", "Smith");

		V1V2Comparison.Result result = compare()
				.v1(HttpMethod.GET, "/customers/all")
				.v2(HttpMethod.GET, ALL_CUSTOMERS_PATH)
				.assertSameJson();

		assertThat(result.v1().getStatusCode().value()).isEqualTo(200);
		assertThat(result.v2().getStatusCode().value()).isEqualTo(200);
		assertThat(result.v1().getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
		assertThat(result.v2().getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
		assertThat(json(result.v2()).findValuesAsText("customerNumber"))
				.contains(String.valueOf(firstNumber), String.valueOf(secondNumber));
		spec(SPEC_GROUP).assertValid(HttpMethod.GET, ALL_CUSTOMERS_PATH, null, result.v2());
	}

	@Test
	void allCustomersWithNullableFieldsMatchesV1() {
		long customerNumber = uniqueNumber();
		Map<String, Object> payload = customerPayload(customerNumber, "John", "Doe");
		payload.remove("middleName");
		assertCreatedCustomer(payload);

		V1V2Comparison.Result result = compare()
				.v1(HttpMethod.GET, "/customers/all")
				.v2(HttpMethod.GET, ALL_CUSTOMERS_PATH)
				.assertSameJson();

		assertThat(json(result.v2()).get(0).get("middleName").isNull()).isTrue();
		spec(SPEC_GROUP).assertValid(HttpMethod.GET, ALL_CUSTOMERS_PATH, null, result.v2());
	}

	@Test
	void allCustomersWithDuplicateCustomerNumberMatchesV1() {
		long customerNumber = uniqueNumber();
		createCustomer(customerNumber, "John", "Doe");
		createCustomer(customerNumber, "Jane", "Doe");

		V1V2Comparison.Result result = compare()
				.v1(HttpMethod.GET, "/customers/all")
				.v2(HttpMethod.GET, ALL_CUSTOMERS_PATH)
				.assertSameJson();

		assertThat(json(result.v2())).hasSize(2);
		assertThat(json(result.v2()).findValuesAsText("customerNumber"))
				.containsExactly(String.valueOf(customerNumber), String.valueOf(customerNumber));
		spec(SPEC_GROUP).assertValid(HttpMethod.GET, ALL_CUSTOMERS_PATH, null, result.v2());
	}

	@Test
	void customerByNumberMatchesV1() {
		long customerNumber = uniqueNumber();
		createCustomer(customerNumber, "John", "Doe");
		String path = "/v2/customers/" + customerNumber;

		V1V2Comparison.Result result = compare()
				.v1(HttpMethod.GET, "/customers/" + customerNumber)
				.v2(HttpMethod.GET, path)
				.assertSameJson();

		assertThat(result.v1().getStatusCode().value()).isEqualTo(200);
		assertThat(result.v2().getStatusCode().value()).isEqualTo(200);
		assertThat(result.v1().getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
		assertThat(result.v2().getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
		assertThat(json(result.v2()).get("customerNumber").asLong()).isEqualTo(customerNumber);
		assertThat(json(result.v2()).get("firstName").asText()).isEqualTo("John");
		spec(SPEC_GROUP).assertValid(HttpMethod.GET, path, null, result.v2());
	}

	@Test
	void missingCustomerReturnsProblemDetailInsteadOfEmptyV1Response() {
		long customerNumber = uniqueNumber();
		String v1Path = "/customers/" + customerNumber;
		String v2Path = "/v2/customers/" + customerNumber;

		V1V2Comparison.Result result = compare()
				.v1(HttpMethod.GET, v1Path)
				.v2(HttpMethod.GET, v2Path)
				.execute();

		assertThat(result.v1().getStatusCode().value()).isEqualTo(200);
		assertThat(result.v1().getBody()).isNullOrEmpty();
		assertThat(result.v2().getStatusCode().value()).isEqualTo(404);
		assertThat(result.v2().getHeaders().getContentType()
				.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
		JsonNode problem = json(result.v2());
		assertThat(problem.get("status").asInt()).isEqualTo(404);
		assertThat(problem.get("detail").asText()).isEqualTo("Customer " + customerNumber + " not found");
		assertThat(problem.get("instance").asText()).endsWith(v2Path);
		spec(SPEC_GROUP).assertValid(HttpMethod.GET, v2Path, null, result.v2());
	}

	@ParameterizedTest
	@ValueSource(strings = { "abc", "99999999999999999999" })
	void invalidCustomerNumberReturnsProblemDetail(String customerNumber) {
		String v1Path = "/customers/" + customerNumber;
		String v2Path = "/v2/customers/" + customerNumber;

		V1V2Comparison.Result result = compare()
				.v1(HttpMethod.GET, v1Path)
				.v2(HttpMethod.GET, v2Path)
				.execute();

		assertThat(result.v1().getStatusCode().value()).isEqualTo(400);
		assertThat(result.v1().getBody()).contains("\"timestamp\"").contains("\"path\"");
		assertThat(result.v1().getHeaders().getContentType()
				.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isFalse();
		assertThat(result.v2().getStatusCode().value()).isEqualTo(400);
		assertThat(result.v2().getHeaders().getContentType()
				.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
		JsonNode problem = json(result.v2());
		assertThat(problem.get("status").asInt()).isEqualTo(400);
		assertThat(problem.has("timestamp")).isFalse();
		spec(SPEC_GROUP).assertValid(HttpMethod.GET, SPEC_VALID_CUSTOMER_PATH, null, result.v2());
	}

	@Test
	void duplicateCustomerNumberReturnsConflictInsteadOfServerError() {
		long customerNumber = uniqueNumber();
		createCustomer(customerNumber, "John", "Doe");
		createCustomer(customerNumber, "Jane", "Doe");
		String v1Path = "/customers/" + customerNumber;
		String v2Path = "/v2/customers/" + customerNumber;

		V1V2Comparison.Result result = compare()
				.v1(HttpMethod.GET, v1Path)
				.v2(HttpMethod.GET, v2Path)
				.execute();

		assertThat(result.v1().getStatusCode().value()).isEqualTo(500);
		assertThat(result.v1().getBody()).contains("\"status\":500");
		assertThat(result.v2().getStatusCode().value()).isEqualTo(409);
		assertThat(result.v2().getHeaders().getContentType()
				.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
		JsonNode problem = json(result.v2());
		assertThat(problem.get("status").asInt()).isEqualTo(409);
		assertThat(problem.get("detail").asText())
				.isEqualTo("Customer number " + customerNumber + " is shared by more than one customer");
		spec(SPEC_GROUP).assertValid(HttpMethod.GET, v2Path, null, result.v2());
	}

	@Test
	void securityHeadersMatchV1ForAllAndByNumberReads() {
		long customerNumber = uniqueNumber();
		createCustomer(customerNumber, "John", "Doe");

		assertSecurityHeadersMatch("/customers/all", ALL_CUSTOMERS_PATH);
		assertSecurityHeadersMatch("/customers/" + customerNumber, "/v2/customers/" + customerNumber);
	}

	private void assertSecurityHeadersMatch(String v1Path, String v2Path) {
		ResponseEntity<String> v1Response = rest.exchange(v1Path, HttpMethod.GET, HttpEntity.EMPTY, String.class);
		ResponseEntity<String> v2Response = rest.exchange(v2Path, HttpMethod.GET, HttpEntity.EMPTY, String.class);

		assertThat(v1Response.getStatusCode().value()).isEqualTo(200);
		assertThat(v2Response.getStatusCode().value()).isEqualTo(200);
		Stream.of(SECURITY_HEADERS).forEach(name ->
				assertThat(v2Response.getHeaders().get(name))
						.as("header %s", name)
						.isEqualTo(v1Response.getHeaders().get(name)));
		spec(SPEC_GROUP).assertValid(HttpMethod.GET, v2Path, null, v2Response);
	}

	private void createCustomer(long customerNumber, String firstName, String lastName) {
		assertCreatedCustomer(customerPayload(customerNumber, firstName, lastName));
	}

	private void assertCreatedCustomer(Map<String, Object> payload) {
		ResponseEntity<String> response = postJson("/customers/add", payload);
		assertThat(response.getStatusCode().value()).isEqualTo(201);
	}
}
