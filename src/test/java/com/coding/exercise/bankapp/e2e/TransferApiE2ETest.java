package com.coding.exercise.bankapp.e2e;

import static com.coding.exercise.bankapp.e2e.ResponseEntityAssertions.assertStatusAndBody;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The API has no dedicated deposit/withdrawal endpoints: an account's opening balance is set
 * on creation, and the only operation that changes balances afterwards is a transfer, which
 * records a DEBIT (withdrawal) on the source account and a CREDIT (deposit) on the target.
 */
class TransferApiE2ETest extends UuidSchemaE2ETestSupport {

	private long customerNumber;
	private long fromAccount;
	private long toAccount;

	private void givenTwoAccounts(double fromBalance, double toBalance) {
		customerNumber = uniqueNumber();
		fromAccount = uniqueNumber();
		toAccount = uniqueNumber();
		createCustomer(customerNumber, "John", "Doe");
		createAccount(customerNumber, fromAccount, fromBalance);
		createAccount(customerNumber, toAccount, toBalance);
	}

	private ResponseEntity<String> transfer(long customer, long from, long to, double amount) {
		return putJson("/accounts/transfer/" + customer, transferPayload(from, to, amount));
	}

	private JsonNode transactions(long accountNumber) {
		ResponseEntity<String> response = get("/accounts/transactions/" + accountNumber);
		assertThat(response.getStatusCode().value()).isEqualTo(200);
		return json(response);
	}

	@Test
	void transferMovesFundsBetweenAccounts() {
		givenTwoAccounts(8740.0, 4355.50);

		ResponseEntity<String> response = transfer(customerNumber, fromAccount, toAccount, 100.5);

		assertStatusAndBody(response, HttpStatus.OK, "Success: Amount transferred for Customer Number " + customerNumber);
		assertThat(balanceOf(fromAccount)).isEqualTo(8639.5);
		assertThat(balanceOf(toAccount)).isEqualTo(4456.0);
	}

	@Test
	void transferRecordsDebitAndCreditTransactions() {
		givenTwoAccounts(500.0, 0.0);

		transfer(customerNumber, fromAccount, toAccount, 125.25);

		JsonNode debits = transactions(fromAccount);
		assertThat(debits).hasSize(1);
		assertThat(debits.get(0).get("accountNumber").asLong()).isEqualTo(fromAccount);
		assertThat(debits.get(0).get("txType").asText()).isEqualTo("DEBIT");
		assertThat(debits.get(0).get("txAmount").asDouble()).isEqualTo(125.25);
		assertThat(debits.get(0).get("txDateTime").isNull()).isFalse();

		JsonNode credits = transactions(toAccount);
		assertThat(credits).hasSize(1);
		assertThat(credits.get(0).get("accountNumber").asLong()).isEqualTo(toAccount);
		assertThat(credits.get(0).get("txType").asText()).isEqualTo("CREDIT");
		assertThat(credits.get(0).get("txAmount").asDouble()).isEqualTo(125.25);
		assertThat(credits.get(0).get("txDateTime").isNull()).isFalse();
	}

	@Test
	void repeatedTransfersAccumulateBalancesAndHistory() {
		givenTwoAccounts(1000.0, 0.0);

		transfer(customerNumber, fromAccount, toAccount, 100.0);
		transfer(customerNumber, fromAccount, toAccount, 200.0);
		transfer(customerNumber, toAccount, fromAccount, 50.0);

		assertThat(balanceOf(fromAccount)).isEqualTo(750.0);
		assertThat(balanceOf(toAccount)).isEqualTo(250.0);
		JsonNode fromHistory = transactions(fromAccount);
		assertThat(fromHistory).hasSize(3);
		assertThat(countOfType(fromHistory, "DEBIT")).isEqualTo(2);
		assertThat(countOfType(fromHistory, "CREDIT")).isEqualTo(1);
		JsonNode toHistory = transactions(toAccount);
		assertThat(toHistory).hasSize(3);
		assertThat(countOfType(toHistory, "DEBIT")).isEqualTo(1);
		assertThat(countOfType(toHistory, "CREDIT")).isEqualTo(2);
	}

	@Test
	void transferOfEntireBalanceIsAllowed() {
		givenTwoAccounts(300.0, 0.0);

		assertStatusAndBody(transfer(customerNumber, fromAccount, toAccount, 300.0), HttpStatus.OK,
				"Success: Amount transferred for Customer Number " + customerNumber);
		assertThat(balanceOf(fromAccount)).isEqualTo(0.0);
		assertThat(balanceOf(toAccount)).isEqualTo(300.0);
	}

	@Test
	void insufficientFundsReturns400AndChangesNothing() {
		givenTwoAccounts(100.0, 50.0);

		ResponseEntity<String> response = transfer(customerNumber, fromAccount, toAccount, 100.01);

		assertStatusAndBody(response, HttpStatus.BAD_REQUEST, "Insufficient Funds.");
		assertThat(balanceOf(fromAccount)).isEqualTo(100.0);
		assertThat(balanceOf(toAccount)).isEqualTo(50.0);
		assertThat(transactions(fromAccount)).isEmpty();
		assertThat(transactions(toAccount)).isEmpty();
	}

	@Test
	void transferForNonexistentCustomerReturns404() {
		givenTwoAccounts(100.0, 0.0);
		long unknownCustomer = uniqueNumber();

		assertStatusAndBody(transfer(unknownCustomer, fromAccount, toAccount, 10.0), HttpStatus.NOT_FOUND,
				"Customer Number " + unknownCustomer + " not found.");
		assertThat(balanceOf(fromAccount)).isEqualTo(100.0);
	}

	@Test
	void transferFromNonexistentAccountReturns404() {
		givenTwoAccounts(100.0, 0.0);
		long unknownAccount = uniqueNumber();

		assertStatusAndBody(transfer(customerNumber, unknownAccount, toAccount, 10.0), HttpStatus.NOT_FOUND,
				"From Account Number " + unknownAccount + " not found.");
		assertThat(balanceOf(toAccount)).isEqualTo(0.0);
	}

	@Test
	void transferToNonexistentAccountReturns404() {
		givenTwoAccounts(100.0, 0.0);
		long unknownAccount = uniqueNumber();

		assertStatusAndBody(transfer(customerNumber, fromAccount, unknownAccount, 10.0), HttpStatus.NOT_FOUND,
				"To Account Number " + unknownAccount + " not found.");
		assertThat(balanceOf(fromAccount)).isEqualTo(100.0);
		assertThat(transactions(fromAccount)).isEmpty();
	}

	@Test
	void transferDoesNotVerifyAccountOwnership() {
		givenTwoAccounts(100.0, 0.0);
		long otherCustomer = uniqueNumber();
		createCustomer(otherCustomer, "Roger", "Federer");

		assertStatusAndBody(transfer(otherCustomer, fromAccount, toAccount, 40.0), HttpStatus.OK,
				"Success: Amount transferred for Customer Number " + otherCustomer);
		assertThat(balanceOf(fromAccount)).isEqualTo(60.0);
		assertThat(balanceOf(toAccount)).isEqualTo(40.0);
	}

	@Test
	void negativeTransferAmountIsAcceptedAndMovesFundsInReverse() {
		givenTwoAccounts(100.0, 50.0);

		assertStatusAndBody(transfer(customerNumber, fromAccount, toAccount, -20.0), HttpStatus.OK,
				"Success: Amount transferred for Customer Number " + customerNumber);
		assertThat(balanceOf(fromAccount)).isEqualTo(120.0);
		assertThat(balanceOf(toAccount)).isEqualTo(30.0);
	}

	@Test
	void transferToSameAccountLeavesBalanceUnchangedButRecordsBothLegs() {
		givenTwoAccounts(100.0, 0.0);

		assertStatusAndBody(transfer(customerNumber, fromAccount, fromAccount, 30.0), HttpStatus.OK,
				"Success: Amount transferred for Customer Number " + customerNumber);
		assertThat(balanceOf(fromAccount)).isEqualTo(100.0);
		JsonNode history = transactions(fromAccount);
		assertThat(history).hasSize(2);
		assertThat(countOfType(history, "DEBIT")).isEqualTo(1);
		assertThat(countOfType(history, "CREDIT")).isEqualTo(1);
	}

	private static long countOfType(JsonNode transactions, String type) {
		long count = 0;
		for (JsonNode tx : transactions) {
			if (type.equals(tx.get("txType").asText())) {
				count++;
			}
		}
		return count;
	}
}
