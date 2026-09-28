package com.coding.exercise.bankapp.e2e;

import static com.coding.exercise.bankapp.e2e.ResponseEntityAssertions.assertCreated;
import static com.coding.exercise.bankapp.e2e.ResponseEntityAssertions.assertEmptyOk;
import static com.coding.exercise.bankapp.e2e.ResponseEntityAssertions.assertServerError;
import static com.coding.exercise.bankapp.e2e.ResponseEntityAssertions.assertStatusAndBody;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;

class CustomerApiE2ETest extends UuidSchemaE2ETestSupport {

	@Test
	void createCustomerReturns201WithPlainTextMessage() {
		long customerNumber = uniqueNumber();

		ResponseEntity<String> response = postJson("/customers/add", customerPayload(customerNumber, "John", "Doe"));

		assertCreated(response, "New Customer created successfully.");
		assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_PLAIN)).isTrue();
	}

	@Test
	void getCustomerByNumberReturnsFullCustomerDetails() {
		long customerNumber = uniqueNumber();
		createCustomer(customerNumber, "John", "Doe");

		ResponseEntity<String> response = get("/customers/" + customerNumber);

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
		JsonNode customer = json(response);
		assertThat(customer.get("customerNumber").asLong()).isEqualTo(customerNumber);
		assertThat(customer.get("firstName").asText()).isEqualTo("John");
		assertThat(customer.get("middleName").asText()).isEqualTo("H");
		assertThat(customer.get("lastName").asText()).isEqualTo("Doe");
		assertThat(customer.get("status").asText()).isEqualTo("Active");
		assertThat(customer.get("contactDetails").get("emailId").asText()).isEqualTo("john@test.com");
		assertThat(customer.get("contactDetails").get("homePhone").asText()).isEqualTo("6150000000");
		assertThat(customer.get("contactDetails").get("workPhone").asText()).isEqualTo("6151112222");
		JsonNode address = customer.get("customerAddress");
		assertThat(address.get("address1").asText()).isEqualTo("123 Domain St");
		assertThat(address.get("address2").asText()).isEqualTo("Suite D");
		assertThat(address.get("city").asText()).isEqualTo("Hermitage");
		assertThat(address.get("state").asText()).isEqualTo("TN");
		assertThat(address.get("zip").asText()).isEqualTo("37076");
		assertThat(address.get("country").asText()).isEqualTo("USA");
		assertThat(customer.has("id")).as("internal id is not exposed").isFalse();
	}

	@Test
	void getNonexistentCustomerReturns200WithEmptyBody() {
		assertEmptyOk(get("/customers/" + uniqueNumber()));
	}

	@Test
	void getAllCustomersReturnsEmptyArrayWhenNoCustomers() {
		ResponseEntity<String> response = get("/customers/all");

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(json(response).isArray()).isTrue();
		assertThat(json(response)).isEmpty();
	}

	@Test
	void getAllCustomersReturnsEveryCreatedCustomer() {
		long first = uniqueNumber();
		long second = uniqueNumber();
		createCustomer(first, "John", "Doe");
		createCustomer(second, "Roger", "Federer");

		ResponseEntity<String> response = get("/customers/all");

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		List<Long> numbers = new ArrayList<>();
		json(response).forEach(node -> numbers.add(node.get("customerNumber").asLong()));
		assertThat(numbers).containsExactlyInAnyOrder(first, second);
	}

	@Test
	void updateCustomerReturns200AndPersistsChanges() {
		long customerNumber = uniqueNumber();
		createCustomer(customerNumber, "John", "Doe");
		Map<String, Object> update = customerPayload(customerNumber, "Roger", "Federer");
		update.put("middleName", "D");
		update.put("status", "Inactive");
		update.put("customerAddress", address("123 McKee Ave", "Chicago", "IL", "60076"));

		ResponseEntity<String> response = putJson("/customers/" + customerNumber, update);

		assertStatusAndBody(response, HttpStatus.OK, "Success: Customer updated.");
		JsonNode customer = json(get("/customers/" + customerNumber));
		assertThat(customer.get("firstName").asText()).isEqualTo("Roger");
		assertThat(customer.get("middleName").asText()).isEqualTo("D");
		assertThat(customer.get("lastName").asText()).isEqualTo("Federer");
		assertThat(customer.get("status").asText()).isEqualTo("Inactive");
		assertThat(customer.get("contactDetails").get("emailId").asText()).isEqualTo("roger@test.com");
		assertThat(customer.get("customerAddress").get("city").asText()).isEqualTo("Chicago");
		assertThat(customer.get("customerAddress").get("zip").asText()).isEqualTo("60076");
	}

	@Test
	void updateNonexistentCustomerReturns404() {
		long customerNumber = uniqueNumber();

		ResponseEntity<String> response = putJson("/customers/" + customerNumber,
				customerPayload(customerNumber, "Ghost", "Customer"));

		assertStatusAndBody(response, HttpStatus.NOT_FOUND, "Customer Number " + customerNumber + " not found.");
	}

	@Test
	void updateWithoutContactDetailsReturns500AndLeavesCustomerUnchanged() {
		long customerNumber = uniqueNumber();
		createCustomer(customerNumber, "John", "Doe");
		Map<String, Object> update = customerPayload(customerNumber, "Roger", "Federer");
		update.remove("contactDetails");

		ResponseEntity<String> response = putJson("/customers/" + customerNumber, update);

		assertServerError(response, CONTEXT_PATH + "/customers/" + customerNumber);
		assertThat(json(get("/customers/" + customerNumber)).get("firstName").asText()).isEqualTo("John");
	}

	@Test
	void deleteCustomerReturns200AndRemovesCustomer() {
		long customerNumber = uniqueNumber();
		createCustomer(customerNumber, "John", "Doe");

		ResponseEntity<String> response = delete("/customers/" + customerNumber);

		assertStatusAndBody(response, HttpStatus.OK, "Success: Customer deleted.");
		assertEmptyOk(get("/customers/" + customerNumber));
		assertThat(json(get("/customers/all"))).isEmpty();
	}

	@Test
	void deleteNonexistentCustomerReturns400() {
		assertStatusAndBody(delete("/customers/" + uniqueNumber()), HttpStatus.BAD_REQUEST, "Customer does not exist.");
	}

	@Test
	void deleteCustomerKeepsTheirAccounts() {
		long customerNumber = uniqueNumber();
		long accountNumber = uniqueNumber();
		createCustomer(customerNumber, "John", "Doe");
		createAccount(customerNumber, accountNumber, 100.0);

		assertStatusAndBody(delete("/customers/" + customerNumber), HttpStatus.OK, "Success: Customer deleted.");

		assertThat(get("/accounts/" + accountNumber).getStatusCode().value()).isEqualTo(302);
	}

	@Test
	void duplicateCustomerNumberIsAcceptedButBreaksLookupByNumber() {
		long customerNumber = uniqueNumber();
		createCustomer(customerNumber, "John", "Doe");

		ResponseEntity<String> duplicate = postJson("/customers/add", customerPayload(customerNumber, "Jane", "Doe"));

		assertCreated(duplicate, "New Customer created successfully.");
		assertThat(json(get("/customers/all"))).hasSize(2);
		String path = "/customers/" + customerNumber;
		assertServerError(get(path), CONTEXT_PATH + path);
		assertServerError(putJson(path, customerPayload(customerNumber, "X", "Y")), CONTEXT_PATH + path);
		assertServerError(delete(path), CONTEXT_PATH + path);
	}

	@Test
	void createCustomerWithoutContactDetailsReturns500AndPersistsNothing() {
		long customerNumber = uniqueNumber();
		Map<String, Object> payload = customerPayload(customerNumber, "No", "Contact");
		payload.remove("contactDetails");

		assertServerError(postJson("/customers/add", payload), CONTEXT_PATH + "/customers/add");
		assertEmptyOk(get("/customers/" + customerNumber));
		assertThat(json(get("/customers/all"))).isEmpty();
	}

	@Test
	void createCustomerWithoutAddressReturns500() {
		Map<String, Object> payload = customerPayload(uniqueNumber(), "No", "Address");
		payload.remove("customerAddress");

		assertServerError(postJson("/customers/add", payload), CONTEXT_PATH + "/customers/add");
		assertThat(json(get("/customers/all"))).isEmpty();
	}

	@Test
	void createCustomerWithMissingBodyReturns400() {
		ResponseEntity<String> response = exchangeJson(HttpMethod.POST, "/customers/add", null);

		assertThat(response.getStatusCode().value()).isEqualTo(400);
		assertThat(response.getBody()).contains("\"status\":400").contains("\"path\":\"/bank-api/customers/add\"");
	}

	@Test
	void createCustomerWithNonJsonContentTypeReturns415() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.TEXT_PLAIN);

		ResponseEntity<String> response = rest.exchange("/customers/add", HttpMethod.POST,
				new HttpEntity<>("not json", headers), String.class);

		assertThat(response.getStatusCode().value()).isEqualTo(415);
	}

	@Test
	void nonNumericCustomerNumberReturns400() {
		ResponseEntity<String> response = get("/customers/abc");

		assertThat(response.getStatusCode().value()).isEqualTo(400);
		assertThat(response.getBody()).contains("\"status\":400").contains("\"path\":\"/bank-api/customers/abc\"");
	}
}
