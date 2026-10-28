package com.coding.exercise.bankapp.v2.moneymovement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.coding.exercise.bankapp.v2.support.JsonComparison;

class MoneyMovementApiTest extends MoneyMovementTestSupport {

	@Test
	void nonFiniteAmountsAreRejectedWithoutSideEffects() {
		long customer = customer();
		long from = account(customer, 100);
		long to = account(customer, 50);
		String path = "/v2/accounts/transfer/" + customer;
		for (String amount : List.of("NaN", "Infinity", "-Infinity")) {
			Map<String, Object> body = new LinkedHashMap<>(transferPayload(from, to, 10));
			body.put("transferAmount", amount);
			problem(HttpMethod.PUT, path, body, putJson(path, body), 400, null);
			assertThat(balance(from)).isEqualTo(100);
			assertThat(balance(to)).isEqualTo(50);
			assertThat(json(history(from))).isEmpty();
			assertThat(json(history(to))).isEmpty();
		}
	}

	@Test
	void publishesV2OperationsOnlyInTheVersionedDocument() {
		var rootPaths = json(get("/v3/api-docs")).get("paths");
		var v2Paths = json(get("/v3/api-docs/v2")).get("paths");
		for (String path : List.of("/v2/accounts/transfer/{customerNumber}",
				"/v2/accounts/transactions/{accountNumber}")) {
			assertThat(rootPaths.has(path)).isFalse();
			assertThat(v2Paths.has(path)).isTrue();
		}
	}

	@Test
	void successfulTransferMatchesV1EffectsOnEquivalentFreshData() {
		long customer = customer();
		long from = account(customer, 100);
		long to = account(customer, 50);
		Map<String, Object> body = transferPayload(from, to, 25);
		String path = "/accounts/transfer/" + customer;
		plain(putJson(path, body), 200, "Success: Amount transferred for Customer Number " + customer);
		double fromBalance = balance(from);
		double toBalance = balance(to);
		String debit = get("/accounts/transactions/" + from).getBody();
		String credit = get("/accounts/transactions/" + to).getBody();

		for (String table : List.of("customer_accountxref", "transaction", "account", "bank_info",
				"customer", "contact", "address")) {
			jdbcTemplate.update("DELETE FROM " + table);
		}
		assertThat(postJson("/customers/add", customerPayload(customer, "Money", "Movement")).getStatusCode().value())
				.isEqualTo(201);
		for (long number : new long[] { from, to }) {
			assertThat(postJson("/accounts/add/" + customer,
					accountPayload(number, number == from ? 100 : 50, "Checking")).getStatusCode().value()).isEqualTo(201);
		}
		ResponseEntity<String> response = putJson("/v2" + path, body);
		valid(HttpMethod.PUT, "/v2" + path, body, response);
		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
		assertThat(json(response).size()).isEqualTo(4);
		assertThat(json(response).get("customerNumber").asLong()).isEqualTo(customer);
		assertThat(json(response).get("fromAccountNumber").asLong()).isEqualTo(from);
		assertThat(json(response).get("toAccountNumber").asLong()).isEqualTo(to);
		assertThat(json(response).get("transferAmount").asDouble()).isEqualTo(25);
		assertThat(balance(from)).isEqualTo(fromBalance).isEqualTo(75);
		assertThat(balance(to)).isEqualTo(toBalance).isEqualTo(75);
		assertThat(JsonComparison.differences(debit, history(from).getBody(), List.of(), List.of("txDateTime"))).isEmpty();
		assertThat(JsonComparison.differences(credit, history(to).getBody(), List.of(), List.of("txDateTime"))).isEmpty();
	}

	@Test
	void historiesMatchForEmptyAndPopulatedAccounts() {
		long customer = customer();
		long from = account(customer, 100);
		long to = account(customer, 50);
		assertThat(json(history(from))).isEmpty();
		ResponseEntity<String> transfer = putJson("/v2/accounts/transfer/" + customer, transferPayload(from, to, 10));
		valid(HttpMethod.PUT, "/v2/accounts/transfer/" + customer, transferPayload(from, to, 10), transfer);
		assertThat(json(history(from))).hasSize(1);
		assertThat(json(history(to))).hasSize(1);
	}

	@Test
	void missingResourcesRetainV1MessagesAsProblems() {
		long customer = customer();
		long from = account(customer, 100);
		long to = account(customer, 50);
		long missing = uniqueNumber();
		missingTransfer(missing, from, to, "Customer Number " + missing + " not found.");
		missingTransfer(customer, missing, to, "From Account Number " + missing + " not found.");
		missingTransfer(customer, from, missing, "To Account Number " + missing + " not found.");
		assertThat(balance(from)).isEqualTo(100);
		assertThat(balance(to)).isEqualTo(50);
		assertThat(json(history(from))).isEmpty();
		assertThat(json(history(to))).isEmpty();
	}

	@Test
	void insufficientFundsIs422AndDoesNotChangeData() {
		long customer = customer();
		long from = account(customer, 100);
		long to = account(customer, 50);
		String path = "/accounts/transfer/" + customer;
		Map<String, Object> body = transferPayload(from, to, 101);
		var result = compare().v1(HttpMethod.PUT, path, body).v2(HttpMethod.PUT, "/v2" + path, body).execute();
		plain(result.v1(), 400, "Insufficient Funds.");
		problem(HttpMethod.PUT, "/v2" + path, body, result.v2(), 422, "Insufficient Funds.");
		assertThat(balance(from)).isEqualTo(100);
		assertThat(balance(to)).isEqualTo(50);
		assertThat(json(history(from))).isEmpty();
		assertThat(json(history(to))).isEmpty();
	}

	@Test
	void missingAndNullFieldsAre400BeforeResourceLookup() {
		long customer = customer();
		long from = account(customer, 100);
		long to = account(customer, 50);
		String path = "/accounts/transfer/" + customer;
		for (String field : List.of("fromAccountNumber", "toAccountNumber", "transferAmount")) {
			for (boolean explicitNull : new boolean[] { false, true }) {
				Map<String, Object> body = new LinkedHashMap<>(transferPayload(from, to, 10));
				if (explicitNull) body.put(field, null);
				else body.remove(field);
				var result = compare().v1(HttpMethod.PUT, path, body).v2(HttpMethod.PUT, "/v2" + path, body).execute();
				if (field.equals("transferAmount")) {
					defaultError(result.v1(), 500);
				} else {
					plain(result.v1(), 404, (field.equals("fromAccountNumber") ? "From" : "To")
							+ " Account Number null not found.");
				}
				problem(HttpMethod.PUT, "/v2" + path, body, result.v2(), 400, null);
			}
		}
		Map<String, Object> invalid = Map.of();
		String missingPath = "/v2/accounts/transfer/" + uniqueNumber();
		problem(HttpMethod.PUT, missingPath, invalid, putJson(missingPath, invalid), 400, null);
		assertThat(balance(from)).isEqualTo(100);
		assertThat(balance(to)).isEqualTo(50);
	}

	@Test
	void zeroAndNegativeAmountsAreRejectedWithoutSideEffects() {
		long customer = customer();
		for (double amount : new double[] { 0, -20 }) {
			long v1From = account(customer, 100);
			long v1To = account(customer, 50);
			long v2From = account(customer, 100);
			long v2To = account(customer, 50);
			String path = "/accounts/transfer/" + customer;
			var body = transferPayload(v2From, v2To, amount);
			var result = compare().v1(HttpMethod.PUT, path, transferPayload(v1From, v1To, amount))
					.v2(HttpMethod.PUT, "/v2" + path, body).execute();
			plain(result.v1(), 200, "Success: Amount transferred for Customer Number " + customer);
			problem(HttpMethod.PUT, "/v2" + path, body, result.v2(), 400, null);
			assertThat(balance(v1From)).isEqualTo(100 - amount);
			assertThat(balance(v1To)).isEqualTo(50 + amount);
			assertThat(json(get("/accounts/transactions/" + v1From))).hasSize(1);
			assertThat(balance(v2From)).isEqualTo(100);
			assertThat(balance(v2To)).isEqualTo(50);
			assertThat(json(history(v2From))).isEmpty();
			assertThat(json(history(v2To))).isEmpty();
		}
	}

	@Test
	void unreadableBodiesAndNonNumericPathsUseProblemDetails() {
		long customer = customer();
		String path = "/accounts/transfer/" + customer;
		for (String body : List.of("", "null", "{broken")) {
			defaultError(putJson(path, body), 400);
			problem(HttpMethod.PUT, "/v2" + path, body, putJson("/v2" + path, body), 400, null);
		}
		Map<String, Object> body = transferPayload(uniqueNumber(), uniqueNumber(), 10);
		defaultError(putJson("/accounts/transfer/abc", body), 400);
		problem(HttpMethod.PUT, "/v2/accounts/transfer/abc", body,
				putJson("/v2/accounts/transfer/abc", body), 400, null);
		defaultError(get("/accounts/transactions/abc"), 400);
		problem(HttpMethod.GET, "/v2/accounts/transactions/abc", null,
				get("/v2/accounts/transactions/abc"), 400, null);
	}

	@Test
	void missingAccountHistoryIs404RatherThanAnEmptyList() {
		String path = "/accounts/transactions/" + uniqueNumber();
		var result = compare().v1(HttpMethod.GET, path).v2(HttpMethod.GET, "/v2" + path).execute();
		assertThat(result.v1().getStatusCode().value()).isEqualTo(200);
		assertThat(result.v1().getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
		assertThat(json(result.v1()).isArray()).isTrue();
		assertThat(json(result.v1())).isEmpty();
		problem(HttpMethod.GET, "/v2" + path, null, result.v2(), 404,
				"Account Number " + path.substring(path.lastIndexOf('/') + 1) + " not found.");
	}

	@Test
	void retainsLackOfOwnershipCheckAndSameAccountSemantics() {
		long owner = customer();
		long other = customer();
		long from = account(owner, 100);
		long to = account(owner, 50);
		String path = "/v2/accounts/transfer/" + other;
		var body = transferPayload(from, to, 10);
		var response = putJson(path, body);
		valid(HttpMethod.PUT, path, body, response);
		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(balance(from)).isEqualTo(90);
		body = transferPayload(from, from, 10);
		response = putJson(path, body);
		valid(HttpMethod.PUT, path, body, response);
		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(balance(from)).isEqualTo(90);
		assertThat(json(history(from))).hasSize(3);
	}

	private ResponseEntity<String> history(long account) {
		String path = "/accounts/transactions/" + account;
		var result = compare().v1(HttpMethod.GET, path).v2(HttpMethod.GET, "/v2" + path).assertSameJson();
		valid(HttpMethod.GET, "/v2" + path, null, result.v2());
		assertThat(result.v1().getStatusCode().value()).isEqualTo(200);
		assertThat(result.v2().getStatusCode().value()).isEqualTo(200);
		return result.v2();
	}

	private void missingTransfer(long customer, long from, long to, String detail) {
		String path = "/accounts/transfer/" + customer;
		var body = transferPayload(from, to, 10);
		var result = compare().v1(HttpMethod.PUT, path, body).v2(HttpMethod.PUT, "/v2" + path, body).execute();
		plain(result.v1(), 404, detail);
		problem(HttpMethod.PUT, "/v2" + path, body, result.v2(), 404, detail);
	}

	private void problem(HttpMethod method, String path, Object body, ResponseEntity<String> response,
			int status, String detail) {
		valid(method, path, body, response);
		assertThat(response.getStatusCode().value()).isEqualTo(status);
		assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
		assertThat(json(response).get("status").asInt()).isEqualTo(status);
		assertThat(json(response).get("title").isTextual()).isTrue();
		assertThat(json(response).get("instance").asText()).isEqualTo(CONTEXT_PATH + path);
		if (detail != null) assertThat(json(response).get("detail").asText()).isEqualTo(detail);
	}

	private void valid(HttpMethod method, String path, Object body, ResponseEntity<String> response) {
		try {
			// The shared validator validates requests too; use a valid request envelope for error responses.
			String validationPath = response.getStatusCode().value() == 400
					? path.substring(0, path.lastIndexOf('/') + 1) + "1" : path;
			String request = method == HttpMethod.PUT ? objectMapper.writeValueAsString(
					response.getStatusCode().value() == 400 ? transferPayload(1, 2, 1) : body) : null;
			spec("money-movement").assertValid(method, validationPath, request, response);
		} catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private void plain(ResponseEntity<String> response, int status, String body) {
		assertThat(response.getStatusCode().value()).isEqualTo(status);
		assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_PLAIN)).isTrue();
		assertThat(response.getBody()).isEqualTo(body);
	}

	private void defaultError(ResponseEntity<String> response, int status) {
		assertThat(response.getStatusCode().value()).isEqualTo(status);
		assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
		assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isFalse();
		assertThat(json(response).get("status").asInt()).isEqualTo(status);
	}
}
