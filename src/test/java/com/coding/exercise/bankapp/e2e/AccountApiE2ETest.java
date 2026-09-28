package com.coding.exercise.bankapp.e2e;

import static com.coding.exercise.bankapp.e2e.ResponseEntityAssertions.assertCreated;
import static com.coding.exercise.bankapp.e2e.ResponseEntityAssertions.assertServerError;
import static com.coding.exercise.bankapp.e2e.ResponseEntityAssertions.assertStatusAndBody;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;

class AccountApiE2ETest extends UuidSchemaE2ETestSupport {

	@Test
	void createAccountForExistingCustomerReturns201() {
		long customerNumber = uniqueNumber();
		createCustomer(customerNumber, "John", "Doe");

		ResponseEntity<String> response = postJson("/accounts/add/" + customerNumber,
				accountPayload(uniqueNumber(), 8740.0, "Checking"));

		assertCreated(response, "New Account created successfully.");
		assertThat(rowCount("account")).isEqualTo(1);
		assertThat(rowCount("customer_accountxref")).isEqualTo(1);
	}

	@Test
	void getAccountReturns302FoundWithAccountDetailsAndOpeningBalance() {
		long customerNumber = uniqueNumber();
		long accountNumber = uniqueNumber();
		createCustomer(customerNumber, "John", "Doe");
		assertCreated(postJson("/accounts/add/" + customerNumber, accountPayload(accountNumber, 4355.50, "Saving")),
				"New Account created successfully.");

		ResponseEntity<String> response = get("/accounts/" + accountNumber);

		assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.FOUND.value());
		assertThat(response.getHeaders().getLocation()).isNull();
		assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
		JsonNode account = json(response);
		assertThat(account.get("accountNumber").asLong()).isEqualTo(accountNumber);
		assertThat(account.get("accountBalance").asDouble()).isEqualTo(4355.50);
		assertThat(account.get("accountStatus").asText()).isEqualTo("Active");
		assertThat(account.get("accountType").asText()).isEqualTo("Saving");
		assertThat(account.get("accountCreated").isNull()).as("accountCreated is not persisted").isTrue();
		JsonNode bank = account.get("bankInformation");
		assertThat(bank.get("branchName").asText()).isEqualTo("Nashville Shores");
		assertThat(bank.get("branchCode").asInt()).isEqualTo(65564);
		assertThat(bank.get("routingNumber").asInt()).isEqualTo(234789876);
		assertThat(bank.get("branchAddress").get("address1").asText()).isEqualTo("500 Branch Rd");
		assertThat(bank.get("branchAddress").get("city").asText()).isEqualTo("Nashville");
		assertThat(account.has("id")).as("internal id is not exposed").isFalse();
	}

	@Test
	void getNonexistentAccountReturns404() {
		long accountNumber = uniqueNumber();

		assertStatusAndBody(get("/accounts/" + accountNumber), HttpStatus.NOT_FOUND,
				"Account Number " + accountNumber + " not found.");
	}

	@Test
	void createAccountForNonexistentCustomerReturns201ButCreatesNothing() {
		long accountNumber = uniqueNumber();

		ResponseEntity<String> response = postJson("/accounts/add/" + uniqueNumber(),
				accountPayload(accountNumber, 100.0, "Checking"));

		assertCreated(response, "New Account created successfully.");
		assertStatusAndBody(get("/accounts/" + accountNumber), HttpStatus.NOT_FOUND,
				"Account Number " + accountNumber + " not found.");
		assertThat(rowCount("account")).isZero();
		assertThat(rowCount("customer_accountxref")).isZero();
	}

	@Test
	void duplicateAccountNumberIsAcceptedButBreaksLookupByNumber() {
		long customerNumber = uniqueNumber();
		long accountNumber = uniqueNumber();
		createCustomer(customerNumber, "John", "Doe");
		createAccount(customerNumber, accountNumber, 100.0);

		ResponseEntity<String> duplicate = postJson("/accounts/add/" + customerNumber,
				accountPayload(accountNumber, 200.0, "Saving"));

		assertCreated(duplicate, "New Account created successfully.");
		assertThat(rowCount("account")).isEqualTo(2);
		String path = "/accounts/" + accountNumber;
		assertServerError(get(path), CONTEXT_PATH + path);
		String txPath = "/accounts/transactions/" + accountNumber;
		assertServerError(get(txPath), CONTEXT_PATH + txPath);
	}

	@Test
	void sameCustomerCanOwnMultipleAccounts() {
		long customerNumber = uniqueNumber();
		long checking = uniqueNumber();
		long saving = uniqueNumber();
		createCustomer(customerNumber, "John", "Doe");

		createAccount(customerNumber, checking, 10.0);
		createAccount(customerNumber, saving, 20.0);

		assertThat(balanceOf(checking)).isEqualTo(10.0);
		assertThat(balanceOf(saving)).isEqualTo(20.0);
		assertThat(rowCount("customer_accountxref")).isEqualTo(2);
	}

	@Test
	void createAccountWithoutBankInformationReturns500AndPersistsNothing() {
		long customerNumber = uniqueNumber();
		long accountNumber = uniqueNumber();
		createCustomer(customerNumber, "John", "Doe");
		Map<String, Object> payload = accountPayload(accountNumber, 100.0, "Checking");
		payload.remove("bankInformation");

		assertServerError(postJson("/accounts/add/" + customerNumber, payload),
				CONTEXT_PATH + "/accounts/add/" + customerNumber);
		assertThat(get("/accounts/" + accountNumber).getStatusCode().value()).isEqualTo(404);
	}

	@Test
	void transactionsOfNewAccountAreEmpty() {
		long customerNumber = uniqueNumber();
		long accountNumber = uniqueNumber();
		createCustomer(customerNumber, "John", "Doe");
		createAccount(customerNumber, accountNumber, 100.0);

		ResponseEntity<String> response = get("/accounts/transactions/" + accountNumber);

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(json(response).isArray()).isTrue();
		assertThat(json(response)).isEmpty();
	}

	@Test
	void transactionsOfNonexistentAccountReturns200WithEmptyArray() {
		ResponseEntity<String> response = get("/accounts/transactions/" + uniqueNumber());

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(json(response).isArray()).isTrue();
		assertThat(json(response)).isEmpty();
	}

	@Test
	void nonNumericAccountNumberReturns400() {
		ResponseEntity<String> response = get("/accounts/abc");

		assertThat(response.getStatusCode().value()).isEqualTo(400);
		assertThat(response.getBody()).contains("\"status\":400").contains("\"path\":\"/bank-api/accounts/abc\"");
	}
}
