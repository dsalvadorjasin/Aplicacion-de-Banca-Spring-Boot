package com.coding.exercise.bankapp.e2e;

import static com.coding.exercise.bankapp.e2e.ResponseEntityAssertions.assertCreated;
import static com.coding.exercise.bankapp.e2e.ResponseEntityAssertions.assertEmptyOk;
import static com.coding.exercise.bankapp.e2e.ResponseEntityAssertions.assertServerError;
import static com.coding.exercise.bankapp.e2e.ResponseEntityAssertions.assertStatusAndBody;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Pins the behavior of the application exactly as configured in production
 * (Hibernate-generated schema, H2 in-memory database).
 *
 * <p>Hibernate 6 maps the {@code UUID} entity ids to native H2 {@code uuid} columns, so the
 * generated schema matches the one used by the {@link UuidSchemaE2ETestSupport} tests and stored
 * customers and accounts can be read back. (Hibernate 5.6 generated {@code binary(255)} columns,
 * which H2 2.x right-padded, making every read of a stored entity fail with 500.)
 */
class DefaultSchemaBaselineE2ETest extends E2ETestSupport {

	@Test
	void idColumnsAreGeneratedAsNativeUuid() {
		String type = jdbcTemplate.queryForObject(
				"SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'CUSTOMER' AND COLUMN_NAME = 'CUST_ID'",
				String.class);

		assertThat(type).isEqualTo("UUID");
	}

	@Test
	void getAllCustomersReturnsEmptyArrayWhenDatabaseIsEmpty() {
		ResponseEntity<String> response = get("/customers/all");

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(json(response)).isEmpty();
	}

	@Test
	void createCustomerReturns201AndPersistsRow() {
		assertCreated(postJson("/customers/add", customerPayload(uniqueNumber(), "John", "Doe")),
				"New Customer created successfully.");

		assertThat(rowCount("customer")).isEqualTo(1);
		assertThat(rowCount("contact")).isEqualTo(1);
		assertThat(rowCount("address")).isEqualTo(1);
	}

	@Test
	void getCreatedCustomerReturnsCustomerDetails() {
		long customerNumber = uniqueNumber();
		createCustomer(customerNumber);

		ResponseEntity<String> response = get("/customers/" + customerNumber);

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(json(response).get("customerNumber").asLong()).isEqualTo(customerNumber);
	}

	@Test
	void getAllCustomersReturnsCreatedCustomer() {
		long customerNumber = uniqueNumber();
		createCustomer(customerNumber);

		ResponseEntity<String> response = get("/customers/all");

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(json(response)).hasSize(1);
		assertThat(json(response).get(0).get("customerNumber").asLong()).isEqualTo(customerNumber);
	}

	@Test
	void updateExistingCustomerReturns200() {
		long customerNumber = uniqueNumber();
		createCustomer(customerNumber);

		assertStatusAndBody(putJson("/customers/" + customerNumber, customerPayload(customerNumber, "Roger", "Federer")),
				HttpStatus.OK, "Success: Customer updated.");
	}

	@Test
	void deleteExistingCustomerReturns200AndRemovesRow() {
		long customerNumber = uniqueNumber();
		createCustomer(customerNumber);

		assertStatusAndBody(delete("/customers/" + customerNumber), HttpStatus.OK, "Success: Customer deleted.");
		assertThat(rowCount("customer")).isZero();
	}

	@Test
	void createAccountForExistingCustomerReturns201AndPersistsAccount() {
		long customerNumber = uniqueNumber();
		long accountNumber = uniqueNumber();
		createCustomer(customerNumber);

		assertCreated(postJson("/accounts/add/" + customerNumber, accountPayload(accountNumber, 100.0, "Checking")),
				"New Account created successfully.");
		assertThat(rowCount("account")).isEqualTo(1);
		ResponseEntity<String> account = get("/accounts/" + accountNumber);
		assertThat(account.getStatusCode().value()).isEqualTo(HttpStatus.FOUND.value());
		assertThat(json(account).get("accountNumber").asLong()).isEqualTo(accountNumber);
	}

	@Test
	void transferFromUnknownAccountOfExistingCustomerReturns404() {
		long customerNumber = uniqueNumber();
		long fromAccountNumber = uniqueNumber();
		createCustomer(customerNumber);

		assertStatusAndBody(putJson("/accounts/transfer/" + customerNumber, transferPayload(fromAccountNumber, uniqueNumber(), 1.0)),
				HttpStatus.NOT_FOUND, "From Account Number " + fromAccountNumber + " not found.");
	}

	@Test
	void duplicateCustomerIsAccepted() {
		long customerNumber = uniqueNumber();
		createCustomer(customerNumber);

		assertCreated(postJson("/customers/add", customerPayload(customerNumber, "Jane", "Doe")),
				"New Customer created successfully.");
		assertThat(rowCount("customer")).isEqualTo(2);
	}

	@Test
	void getNonexistentCustomerReturns200WithEmptyBody() {
		assertEmptyOk(get("/customers/" + uniqueNumber()));
	}

	@Test
	void updateNonexistentCustomerReturns404() {
		long customerNumber = uniqueNumber();

		assertStatusAndBody(putJson("/customers/" + customerNumber, customerPayload(customerNumber, "Ghost", "Customer")),
				HttpStatus.NOT_FOUND, "Customer Number " + customerNumber + " not found.");
	}

	@Test
	void deleteNonexistentCustomerReturns400() {
		assertStatusAndBody(delete("/customers/" + uniqueNumber()), HttpStatus.BAD_REQUEST, "Customer does not exist.");
	}

	@Test
	void createCustomerWithoutContactDetailsReturns500AndPersistsNothing() {
		Map<String, Object> payload = customerPayload(uniqueNumber(), "No", "Contact");
		payload.remove("contactDetails");

		assertServerError(postJson("/customers/add", payload), CONTEXT_PATH + "/customers/add");
		assertThat(rowCount("customer")).isZero();
	}

	@Test
	void createAccountForNonexistentCustomerReturns201ButCreatesNothing() {
		long accountNumber = uniqueNumber();

		assertCreated(postJson("/accounts/add/" + uniqueNumber(), accountPayload(accountNumber, 100.0, "Checking")),
				"New Account created successfully.");
		assertThat(rowCount("account")).isZero();
		assertStatusAndBody(get("/accounts/" + accountNumber), HttpStatus.NOT_FOUND,
				"Account Number " + accountNumber + " not found.");
	}

	@Test
	void transferForNonexistentCustomerReturns404() {
		long customerNumber = uniqueNumber();

		assertStatusAndBody(putJson("/accounts/transfer/" + customerNumber, transferPayload(uniqueNumber(), uniqueNumber(), 1.0)),
				HttpStatus.NOT_FOUND, "Customer Number " + customerNumber + " not found.");
	}

	@Test
	void transactionsOfNonexistentAccountReturns200WithEmptyArray() {
		ResponseEntity<String> response = get("/accounts/transactions/" + uniqueNumber());

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(json(response)).isEmpty();
	}

	private void createCustomer(long customerNumber) {
		assertCreated(postJson("/customers/add", customerPayload(customerNumber, "John", "Doe")),
				"New Customer created successfully.");
	}
}
