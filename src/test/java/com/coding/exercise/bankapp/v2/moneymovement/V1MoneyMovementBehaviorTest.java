package com.coding.exercise.bankapp.v2.moneymovement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class V1MoneyMovementBehaviorTest extends MoneyMovementTestSupport {

	@Test
	void pinsNonFiniteAmounts() {
		long customer = customer();
		for (String amount : new String[] { "NaN", "Infinity", "-Infinity" }) {
			long from = account(customer, 100);
			long to = account(customer, 50);
			Map<String, Object> body = new LinkedHashMap<>(transferPayload(from, to, 10));
			body.put("transferAmount", amount);
			ResponseEntity<String> response = putJson("/accounts/transfer/" + customer, body);
			if (amount.equals("Infinity")) {
				plain(response, 400, "Insufficient Funds.");
				assertThat(balance(from)).isEqualTo(100);
				assertThat(balance(to)).isEqualTo(50);
				assertThat(json(get("/accounts/transactions/" + from))).isEmpty();
				assertThat(json(get("/accounts/transactions/" + to))).isEmpty();
			} else {
				plain(response, 200, "Success: Amount transferred for Customer Number " + customer);
				if (amount.equals("NaN")) {
					assertThat(balance(from)).isNaN();
					assertThat(balance(to)).isNaN();
				} else {
					assertThat(balance(from)).isEqualTo(Double.POSITIVE_INFINITY);
					assertThat(balance(to)).isEqualTo(Double.NEGATIVE_INFINITY);
				}
				for (long account : new long[] { from, to }) {
					var history = json(get("/accounts/transactions/" + account));
					assertThat(history).hasSize(1);
					assertThat(history.get(0).get("txAmount").asText()).isEqualTo(amount);
				}
			}
		}
	}

	@Test
	void pinsSuccessAndNonPositiveAmounts() {
		long customer = customer();
		long from = account(customer, 100);
		long to = account(customer, 50);
		for (double amount : new double[] { 10, 0, -20 }) {
			plain(putJson("/accounts/transfer/" + customer, transferPayload(from, to, amount)), 200,
					"Success: Amount transferred for Customer Number " + customer);
		}
		assertThat(balance(from)).isEqualTo(110);
		assertThat(balance(to)).isEqualTo(40);
		assertThat(json(get("/accounts/transactions/" + from))).hasSize(3);
	}

	@Test
	void pinsMissingResourcesAndInsufficientFunds() {
		long customer = customer();
		long from = account(customer, 100);
		long to = account(customer, 50);
		long missing = uniqueNumber();
		plain(putJson("/accounts/transfer/" + missing, transferPayload(from, to, 10)), 404,
				"Customer Number " + missing + " not found.");
		plain(putJson("/accounts/transfer/" + customer, transferPayload(missing, to, 10)), 404,
				"From Account Number " + missing + " not found.");
		plain(putJson("/accounts/transfer/" + customer, transferPayload(from, missing, 10)), 404,
				"To Account Number " + missing + " not found.");
		plain(putJson("/accounts/transfer/" + customer, transferPayload(from, to, 101)), 400,
				"Insufficient Funds.");
		assertThat(balance(from)).isEqualTo(100);
		assertThat(balance(to)).isEqualTo(50);
		assertThat(json(get("/accounts/transactions/" + missing))).isEmpty();
		assertThat(get("/accounts/transactions/" + missing).getStatusCode().value()).isEqualTo(200);
	}

	@Test
	void pinsNullAndMissingFieldsAndUnreadableRequests() {
		long customer = customer();
		long from = account(customer, 100);
		long to = account(customer, 50);
		String path = "/accounts/transfer/" + customer;
		for (String field : new String[] { "fromAccountNumber", "toAccountNumber", "transferAmount" }) {
			for (boolean explicitNull : new boolean[] { false, true }) {
				Map<String, Object> body = new LinkedHashMap<>(transferPayload(from, to, 10));
				if (explicitNull) body.put(field, null);
				else body.remove(field);
				ResponseEntity<String> response = putJson(path, body);
				if (field.equals("transferAmount")) {
					assertThat(response.getStatusCode().value()).isEqualTo(500);
					assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
					assertThat(json(response).get("status").asInt()).isEqualTo(500);
				} else {
					plain(response, 404, (field.equals("fromAccountNumber") ? "From" : "To")
							+ " Account Number null not found.");
				}
			}
		}
		for (String body : new String[] { "", "null", "{broken" }) {
			assertThat(putJson(path, body).getStatusCode().value()).isEqualTo(400);
		}
		assertThat(putJson("/accounts/transfer/abc", transferPayload(from, to, 10)).getStatusCode().value()).isEqualTo(400);
		assertThat(get("/accounts/transactions/abc").getStatusCode().value()).isEqualTo(400);
	}

	private void plain(ResponseEntity<String> response, int status, String body) {
		assertThat(response.getStatusCode().value()).isEqualTo(status);
		assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_PLAIN)).isTrue();
		assertThat(response.getBody()).isEqualTo(body);
	}
}
