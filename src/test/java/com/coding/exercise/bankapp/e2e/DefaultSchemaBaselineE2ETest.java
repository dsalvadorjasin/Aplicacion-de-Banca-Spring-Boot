package com.coding.exercise.bankapp.e2e;

import static com.coding.exercise.bankapp.e2e.ResponseEntityAssertions.assertCreated;
import static com.coding.exercise.bankapp.e2e.ResponseEntityAssertions.assertEmptyOk;
import static com.coding.exercise.bankapp.e2e.ResponseEntityAssertions.assertServerError;
import static com.coding.exercise.bankapp.e2e.ResponseEntityAssertions.assertStatusAndBody;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Pins the behavior of the application exactly as configured in production
 * (Hibernate-generated schema, H2 in-memory database).
 *
 * <p>Hibernate 5.6 maps the {@code UUID} entity ids to {@code binary(255)} columns. H2 2.x treats
 * {@code BINARY(n)} as fixed length and right-pads the stored 16-byte ids, so loading an entity
 * by its id (every eager association such as Customer.contactDetails or Account.bankInformation)
 * fails with {@code EntityNotFoundException}. Every endpoint that reads a stored customer or
 * account therefore returns 500. Writes that do not read back succeed.
 *
 * <p>Tests tagged {@code h2-binary-uuid-defect} are expected to change if the persistence stack
 * (Hibernate/H2 versions or id mapping) changes; they document the current defect, not the
 * intended contract. The intended contract is covered by the {@link UuidSchemaE2ETestSupport} tests.
 */
class DefaultSchemaBaselineE2ETest extends E2ETestSupport {

	@Test
	void idColumnsAreGeneratedAsFixedLengthBinary() {
		String type = jdbcTemplate.queryForObject(
				"SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'CUSTOMER' AND COLUMN_NAME = 'CUST_ID'",
				String.class);

		assertThat(type).isEqualTo("BINARY");
	}

	@Test
	void getAllCustomersReturnsEmptyArrayWhenDatabaseIsEmpty() {
		ResponseEntity<String> response = get("/customers/all");

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(json(response)).isEmpty();
	}

	@Test
	@Tag("h2-binary-uuid-defect")
	void createCustomerReturns201AndPersistsRow() {
		assertCreated(postJson("/customers/add", customerPayload(uniqueNumber(), "John", "Doe")),
				"New Customer created successfully.");

		assertThat(rowCount("customer")).isEqualTo(1);
		assertThat(rowCount("contact")).isEqualTo(1);
		assertThat(rowCount("address")).isEqualTo(1);
	}

	@Test
	@Tag("h2-binary-uuid-defect")
	void getCreatedCustomerReturns500() {
		long customerNumber = uniqueNumber();
		createCustomer(customerNumber);

		String path = "/customers/" + customerNumber;
		assertServerError(get(path), CONTEXT_PATH + path);
	}

	@Test
	@Tag("h2-binary-uuid-defect")
	void getAllCustomersReturns500OnceAnyCustomerExists() {
		createCustomer(uniqueNumber());

		assertServerError(get("/customers/all"), CONTEXT_PATH + "/customers/all");
	}

	@Test
	@Tag("h2-binary-uuid-defect")
	void updateExistingCustomerReturns500() {
		long customerNumber = uniqueNumber();
		createCustomer(customerNumber);

		String path = "/customers/" + customerNumber;
		assertServerError(putJson(path, customerPayload(customerNumber, "Roger", "Federer")), CONTEXT_PATH + path);
	}

	@Test
	@Tag("h2-binary-uuid-defect")
	void deleteExistingCustomerReturns500AndKeepsRow() {
		long customerNumber = uniqueNumber();
		createCustomer(customerNumber);

		String path = "/customers/" + customerNumber;
		assertServerError(delete(path), CONTEXT_PATH + path);
		assertThat(rowCount("customer")).isEqualTo(1);
	}

	@Test
	@Tag("h2-binary-uuid-defect")
	void createAccountForExistingCustomerReturns500AndPersistsNothing() {
		long customerNumber = uniqueNumber();
		long accountNumber = uniqueNumber();
		createCustomer(customerNumber);

		String path = "/accounts/add/" + customerNumber;
		assertServerError(postJson(path, accountPayload(accountNumber, 100.0, "Checking")), CONTEXT_PATH + path);
		assertThat(rowCount("account")).isZero();
		assertStatusAndBody(get("/accounts/" + accountNumber), HttpStatus.NOT_FOUND,
				"Account Number " + accountNumber + " not found.");
	}

	@Test
	@Tag("h2-binary-uuid-defect")
	void transferForExistingCustomerReturns500() {
		long customerNumber = uniqueNumber();
		createCustomer(customerNumber);

		String path = "/accounts/transfer/" + customerNumber;
		assertServerError(putJson(path, transferPayload(uniqueNumber(), uniqueNumber(), 1.0)), CONTEXT_PATH + path);
	}

	@Test
	@Tag("h2-binary-uuid-defect")
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
