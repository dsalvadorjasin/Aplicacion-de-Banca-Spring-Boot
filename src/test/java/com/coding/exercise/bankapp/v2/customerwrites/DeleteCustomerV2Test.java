package com.coding.exercise.bankapp.v2.customerwrites;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class DeleteCustomerV2Test extends CustomerWritesTestSupport {

	@Test
	void deleteReturnsNoContentAndRemovesTheCustomer() {
		long v1Number = uniqueNumber();
		long v2Number = uniqueNumber();
		createCustomerV1(v1Number);
		createCustomerV1(v2Number);

		ResponseEntity<String> v1 = delete("/customers/" + v1Number);
		ResponseEntity<String> v2 = delete("/v2/customers/" + v2Number);
		spec("customer-writes").assertValid(HttpMethod.DELETE, "/v2/customers/" + v2Number, null, v2);

		assertThat(v1.getStatusCode().value()).isEqualTo(200);
		assertThat(v1.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_PLAIN)).isTrue();
		assertThat(v1.getBody()).isEqualTo("Success: Customer deleted.");
		assertThat(v2.getStatusCode().value()).isEqualTo(204);
		assertThat(v2.getBody()).isNullOrEmpty();
		assertThat(get("/customers/" + v1Number).getBody()).isNullOrEmpty();
		assertThat(get("/customers/" + v2Number).getBody()).isNullOrEmpty();
		assertThat(rowCount("customer")).isZero();
	}

	@Test
	void deletingCustomerKeepsTheirAccounts() {
		long customerNumber = uniqueNumber();
		long accountNumber = uniqueNumber();
		createCustomerV1(customerNumber);
		createAccountV1(customerNumber, accountNumber);

		ResponseEntity<String> v2 = delete("/v2/customers/" + customerNumber);
		spec("customer-writes").assertValid(HttpMethod.DELETE, "/v2/customers/" + customerNumber, null, v2);

		assertThat(v2.getStatusCode().value()).isEqualTo(204);
		assertThat(get("/accounts/" + accountNumber).getStatusCode().value()).isEqualTo(302);
		assertThat(rowCount("account")).isEqualTo(1);
	}

	@Test
	void missingCustomerReturnsProblemDetailOnV2AndV1BadRequest() {
		long customerNumber = uniqueNumber();
		ResponseEntity<String> v1 = delete("/customers/" + customerNumber);
		assertThat(v1.getStatusCode().value()).isEqualTo(400);
		assertThat(v1.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_PLAIN)).isTrue();
		assertThat(v1.getBody()).isEqualTo("Customer does not exist.");

		ResponseEntity<String> v2 = delete("/v2/customers/" + customerNumber);
		spec("customer-writes").assertValid(HttpMethod.DELETE, "/v2/customers/" + customerNumber, null, v2);
		assertProblem(v2, 404);
		assertThat(json(v2).path("detail").asText()).contains(Long.toString(customerNumber));
	}

	@Test
	void duplicateCustomerNumberReturnsConflictOnV2AndServerErrorOnV1() {
		long customerNumber = uniqueNumber();
		createCustomerV1(customerNumber);
		createCustomerV1(customerNumber);

		ResponseEntity<String> v2 = delete("/v2/customers/" + customerNumber);
		spec("customer-writes").assertValid(HttpMethod.DELETE, "/v2/customers/" + customerNumber, null, v2);
		assertProblem(v2, 409);
		assertThat(rowCount("customer")).isEqualTo(2);

		assertThat(delete("/customers/" + customerNumber).getStatusCode().value()).isEqualTo(500);
	}

	@Test
	void nonNumericCustomerNumberReturnsV2ProblemAndV1DefaultError() {
		ResponseEntity<String> v2 = delete("/v2/customers/abc");
		assertResponseConformsToSpec(HttpMethod.DELETE, "/v2/customers/abc", null, v2);
		assertProblem(v2, 400);

		assertV1DefaultErrorJson(delete("/customers/abc"), 400);
	}
}
