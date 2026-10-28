package com.coding.exercise.bankapp.v2.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.coding.exercise.bankapp.v2.support.ApiV2SpecValidator;
import com.coding.exercise.bankapp.v2.support.ApiV2TestSupport;
import com.coding.exercise.bankapp.v2.support.JsonComparison;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountsV2E2ETest extends ApiV2TestSupport {

	private static final ApiV2SpecValidator SPEC = spec("accounts");

	@Test
	void accountsArePublishedInV2GroupWithoutChangingTheLegacyDocument() {
		var rootPaths = json(get("/v3/api-docs")).path("paths");
		var v1Paths = json(get("/v3/api-docs/v1")).path("paths");
		var v2Paths = json(get("/v3/api-docs/v2")).path("paths");
		assertThat(rootPaths.has("/v2/accounts/{accountNumber}")).isFalse();
		assertThat(rootPaths.has("/v2/accounts/add/{customerNumber}")).isFalse();
		assertThat(v1Paths.has("/v2/accounts/{accountNumber}")).isFalse();
		assertThat(v2Paths.path("/v2/accounts/{accountNumber}").has("get")).isTrue();
		assertThat(v2Paths.path("/v2/accounts/add/{customerNumber}").has("post")).isTrue();
	}

	@Test
	void successfulReadReturns200InsteadOf302WithExactlyTheSameDataAndSecurityHeaders() {
		long customer = createCustomer();
		long account = uniqueNumber();
		assertThat(postJson("/accounts/add/" + customer, accountPayload(account, 4355.50, "Saving"))
				.getStatusCode().value()).isEqualTo(201);

		var result = compare().v1(HttpMethod.GET, "/accounts/" + account)
				.v2(HttpMethod.GET, "/v2/accounts/" + account).assertSameJson();

		assertThat(result.v1().getStatusCode().value()).isEqualTo(302);
		assertThat(result.v2().getStatusCode().value()).isEqualTo(200);
		assertThat(result.v1().getHeaders().getLocation()).isNull();
		assertThat(result.v2().getHeaders().getLocation()).isNull();
		assertThat(json(result.v2()).get("accountCreated").isNull()).isTrue();
		assertThat(json(result.v2()).get("accountBalance").asDouble()).isEqualTo(4355.50);
		assertThat(json(result.v2()).has("id")).isFalse();
		assertThat(result.v2().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
		for (String header : List.of("X-Content-Type-Options", "X-Frame-Options", "Cache-Control")) {
			assertThat(result.v2().getHeaders().get(header)).isEqualTo(result.v1().getHeaders().get(header));
		}
		SPEC.assertValid(HttpMethod.GET, "/v2/accounts/" + account, null, result.v2());
	}

	@Test
	void missingAccountReturns404ProblemDetailInsteadOfPlainText() {
		long number = uniqueNumber();
		var result = compare().v1(HttpMethod.GET, "/accounts/" + number)
				.v2(HttpMethod.GET, "/v2/accounts/" + number).execute();

		assertThat(result.v1().getStatusCode().value()).isEqualTo(404);
		assertThat(result.v1().getBody()).isEqualTo("Account Number " + number + " not found.");
		assertThat(result.v1().getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_PLAIN)).isTrue();
		assertProblem(result.v2(), 404, "/v2/accounts/" + number);
		SPEC.assertValid(HttpMethod.GET, "/v2/accounts/" + number, null, result.v2());
	}

	@ParameterizedTest
	@CsvSource({"0,404", "-1,404", "abc,400", "9223372036854775808,400"})
	void invalidLookupHasProblemDetailAndNonPositiveNumbersAre400(String number, int v1Status) {
		var result = compare().v1(HttpMethod.GET, "/accounts/" + number)
				.v2(HttpMethod.GET, "/v2/accounts/" + number).execute();

		assertThat(result.v1().getStatusCode().value()).isEqualTo(v1Status);
		if (v1Status == 404) {
			assertThat(result.v1().getBody()).isEqualTo("Account Number " + number + " not found.");
		} else {
			assertThat(json(result.v1()).has("path")).isTrue();
			assertThat(result.v1().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
		}
		assertProblem(result.v2(), 400, "/v2/accounts/" + number);
		assertResponseValid(HttpMethod.GET, "/v2/accounts/" + number, null, result.v2());
	}

	@Test
	void creationReturnsPersistedJsonAndDereferenceableLocationWithSameDataAsV1() throws Exception {
		long customer = createCustomer();
		long number = uniqueNumber();
		Map<String, Object> payload = accountPayload(number, 100.25, "Checking");
		ResponseEntity<String> v1 = postJson("/accounts/add/" + customer, payload);
		assertV1Created(v1);
		String persistedByV1 = get("/accounts/" + number).getBody();
		jdbcTemplate.update("DELETE FROM customer_accountxref");
		jdbcTemplate.update("DELETE FROM account");

		String path = "/v2/accounts/add/" + customer;
		ResponseEntity<String> v2 = postJson(path, payload);
		SPEC.assertValid(HttpMethod.POST, path, objectMapper.writeValueAsString(payload), v2);
		assertThat(v2.getStatusCode().value()).isEqualTo(201);
		assertThat(v2.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
		assertThat(v2.getHeaders().getLocation()).hasToString(CONTEXT_PATH + "/v2/accounts/" + number);
		JsonComparison.assertEquivalent(persistedByV1, v2.getBody(), List.of(), List.of());
		assertThat(json(v2).get("accountCreated").isNull()).isTrue();
		assertThat(rowCount("account")).isEqualTo(1);
		assertThat(rowCount("customer_accountxref")).isEqualTo(1);

		ResponseEntity<String> located = rest.getForEntity(
				"http://localhost:" + port + v2.getHeaders().getLocation(), String.class);
		SPEC.assertValid(HttpMethod.GET, "/v2/accounts/" + number, null, located);
		assertThat(located.getStatusCode().value()).isEqualTo(200);
		JsonComparison.assertEquivalent(v2.getBody(), located.getBody(), List.of(), List.of());
		var reads = compare().v1(HttpMethod.GET, "/accounts/" + number)
				.v2(HttpMethod.GET, "/v2/accounts/" + number).assertSameJson();
		SPEC.assertValid(HttpMethod.GET, "/v2/accounts/" + number, null, reads.v2());
	}

	@Test
	void missingCustomerReturns404RatherThanFalseSuccessAndNothingIsPersisted() throws Exception {
		long customer = uniqueNumber();
		var payload = accountPayload(uniqueNumber(), 25.0, "Saving");
		String path = "/v2/accounts/add/" + customer;
		var result = compare().v1(HttpMethod.POST, "/accounts/add/" + customer, payload)
				.v2(HttpMethod.POST, path, payload).execute();

		assertV1Created(result.v1());
		assertProblem(result.v2(), 404, path);
		SPEC.assertValid(HttpMethod.POST, path, objectMapper.writeValueAsString(payload), result.v2());
		assertNoAccounts();
	}

	@Test
	void duplicateIs409WithoutSideEffectsWhileV1InsertsDuplicateAndBreaksRead() throws Exception {
		long customer = createCustomer();
		long number = uniqueNumber();
		var original = accountPayload(number, 100.0, "Checking");
		assertV1Created(postJson("/accounts/add/" + customer, original));
		var duplicate = accountPayload(number, 200.0, "Saving");
		String path = "/v2/accounts/add/" + customer;

		ResponseEntity<String> v2 = postJson(path, duplicate);
		assertProblem(v2, 409, path);
		SPEC.assertValid(HttpMethod.POST, path, objectMapper.writeValueAsString(duplicate), v2);
		assertThat(rowCount("account")).isEqualTo(1);
		assertThat(rowCount("customer_accountxref")).isEqualTo(1);
		assertThat(rowCount("bank_info")).isEqualTo(1);
		assertThat(json(get("/accounts/" + number)).get("accountBalance").asDouble()).isEqualTo(100.0);

		assertV1Created(postJson("/accounts/add/" + customer, duplicate));
		assertThat(rowCount("account")).isEqualTo(2);
		assertThat(get("/accounts/" + number).getStatusCode().value()).isEqualTo(500);
	}

	@Test
	void legacyDuplicatesStillRejectCreationAndLookupReturnsProblemDetail() throws Exception {
		long customer = createCustomer();
		long number = uniqueNumber();
		var payload = accountPayload(number, 100.0, "Checking");
		assertV1Created(postJson("/accounts/add/" + customer, payload));
		assertV1Created(postJson("/accounts/add/" + customer, payload));

		String path = "/v2/accounts/add/" + customer;
		ResponseEntity<String> duplicate = postJson(path, payload);
		assertProblem(duplicate, 409, path);
		SPEC.assertValid(HttpMethod.POST, path, objectMapper.writeValueAsString(payload), duplicate);
		assertThat(rowCount("account")).isEqualTo(2);
		assertThat(rowCount("customer_accountxref")).isEqualTo(2);

		var reads = compare().v1(HttpMethod.GET, "/accounts/" + number)
				.v2(HttpMethod.GET, "/v2/accounts/" + number).execute();
		assertThat(reads.v1().getStatusCode().value()).isEqualTo(500);
		assertThat(json(reads.v1()).has("path")).isTrue();
		assertProblem(reads.v2(), 500, "/v2/accounts/" + number);
		SPEC.assertValid(HttpMethod.GET, "/v2/accounts/" + number, null, reads.v2());
	}

	@ParameterizedTest
	@CsvSource({"missing-number,201", "zero-number,201", "negative-number,201", "missing-balance,201",
			"negative-balance,201", "non-finite-balance,201", "missing-bank,500", "missing-address,500"})
	void invalidAccountReturns400InsteadOfV1AcceptanceOr500(String invalidField, int v1Status) throws Exception {
		long customer = createCustomer();
		var payload = accountPayload(uniqueNumber(), 100.0, "Checking");
		switch (invalidField) {
			case "missing-number" -> payload.remove("accountNumber");
			case "zero-number" -> payload.put("accountNumber", 0);
			case "negative-number" -> payload.put("accountNumber", -1);
			case "missing-balance" -> payload.remove("accountBalance");
			case "negative-balance" -> payload.put("accountBalance", -1.0);
			case "non-finite-balance" -> payload.put("accountBalance", "NaN");
			case "missing-bank" -> payload.remove("bankInformation");
			case "missing-address" -> payload.put("bankInformation", Map.of("branchName", "Branch"));
			default -> throw new IllegalArgumentException(invalidField);
		}
		String path = "/v2/accounts/add/" + customer;
		ResponseEntity<String> v2 = postJson(path, payload);
		assertProblem(v2, 400, path);
		assertResponseValid(HttpMethod.POST, path, objectMapper.writeValueAsString(payload), v2);
		assertNoAccounts();
		assertThat(rowCount("bank_info")).isZero();

		ResponseEntity<String> v1 = postJson("/accounts/add/" + customer, payload);
		assertThat(v1.getStatusCode().value()).isEqualTo(v1Status);
		if (v1Status == 201) {
			assertV1Created(v1);
			assertThat(rowCount("account")).isEqualTo(1);
		} else {
			assertThat(v1.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
			assertThat(json(v1).get("status").asInt()).isEqualTo(500);
			assertNoAccounts();
		}
	}

	@ParameterizedTest
	@ValueSource(longs = {0, -1})
	void nonPositiveCustomerNumberIs400InsteadOfV1FalseSuccess(long customer) throws Exception {
		var payload = accountPayload(uniqueNumber(), 1.0, "Checking");
		String path = "/v2/accounts/add/" + customer;
		var result = compare().v1(HttpMethod.POST, "/accounts/add/" + customer, payload)
				.v2(HttpMethod.POST, path, payload).execute();
		assertV1Created(result.v1());
		assertProblem(result.v2(), 400, path);
		assertResponseValid(HttpMethod.POST, path, objectMapper.writeValueAsString(payload), result.v2());
		assertNoAccounts();
	}

	@ParameterizedTest
	@ValueSource(strings = {"{", "null", "{\"accountNumber\":\"abc\"}", "{\"accountBalance\":\"abc\"}"})
	void malformedBodyKeeps400ButUsesProblemDetail(String body) {
		long customer = createCustomer();
		String path = "/v2/accounts/add/" + customer;
		var result = compare().v1(HttpMethod.POST, "/accounts/add/" + customer, body)
				.v2(HttpMethod.POST, path, body).execute();
		assertThat(result.v1().getStatusCode().value()).isEqualTo(400);
		assertThat(result.v1().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
		assertThat(json(result.v1()).has("path")).isTrue();
		assertProblem(result.v2(), 400, path);
		assertResponseValid(HttpMethod.POST, path, body, result.v2());
		assertNoAccounts();
	}

	@Test
	void malformedCustomerNumberIs400ProblemDetail() throws Exception {
		var payload = accountPayload(uniqueNumber(), 0.0, "Checking");
		String path = "/v2/accounts/add/abc";
		var result = compare().v1(HttpMethod.POST, "/accounts/add/abc", payload)
				.v2(HttpMethod.POST, path, payload).execute();
		assertThat(result.v1().getStatusCode().value()).isEqualTo(400);
		assertThat(json(result.v1()).has("path")).isTrue();
		assertProblem(result.v2(), 400, path);
		assertResponseValid(HttpMethod.POST, path, objectMapper.writeValueAsString(payload), result.v2());
		assertNoAccounts();
	}

	@Test
	void unsupportedContentTypeKeeps415ButUsesProblemDetail() {
		long customer = createCustomer();
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.TEXT_PLAIN);
		HttpEntity<String> request = new HttpEntity<>("not JSON", headers);
		ResponseEntity<String> v1 = rest.exchange("/accounts/add/" + customer, HttpMethod.POST, request, String.class);
		String path = "/v2/accounts/add/" + customer;
		ResponseEntity<String> v2 = rest.exchange(path, HttpMethod.POST, request, String.class);
		assertThat(v1.getStatusCode().value()).isEqualTo(415);
		assertThat(json(v1).has("path")).isTrue();
		assertProblem(v2, 415, path);
		assertResponseValid(HttpMethod.POST, path, "not JSON", v2);
		assertNoAccounts();
	}

	@Test
	void unsupportedResponseTypeKeeps406ButUsesProblemDetail() {
		long customer = createCustomer();
		long number = uniqueNumber();
		assertV1Created(postJson("/accounts/add/" + customer, accountPayload(number, 1.0, "Checking")));
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(List.of(MediaType.TEXT_PLAIN));
		HttpEntity<Void> request = new HttpEntity<>(headers);
		ResponseEntity<String> v1 = rest.exchange("/accounts/" + number, HttpMethod.GET, request, String.class);
		String path = "/v2/accounts/" + number;
		ResponseEntity<String> v2 = rest.exchange(path, HttpMethod.GET, request, String.class);
		assertThat(v1.getStatusCode().value()).isEqualTo(406);
		assertProblem(v2, 406, path);
		SPEC.assertValid(HttpMethod.GET, path, null, v2);
	}

	@Test
	void zeroBalanceNullableOptionalFieldsAndMultipleAccountsKeepV1Data() throws Exception {
		long customer = createCustomer();
		for (int index = 0; index < 2; index++) {
			long number = uniqueNumber();
			var payload = accountPayload(number, 0.0, "Checking");
			payload.put("accountStatus", null);
			payload.put("accountType", null);
			payload.put("bankInformation", Map.of("branchAddress", Map.of()));
			String path = "/v2/accounts/add/" + customer;
			ResponseEntity<String> created = postJson(path, payload);
			SPEC.assertValid(HttpMethod.POST, path, objectMapper.writeValueAsString(payload), created);
			assertThat(created.getStatusCode().value()).isEqualTo(201);
			var result = compare().v1(HttpMethod.GET, "/accounts/" + number)
					.v2(HttpMethod.GET, "/v2/accounts/" + number).assertSameJson();
			SPEC.assertValid(HttpMethod.GET, "/v2/accounts/" + number, null, result.v2());
		}
		assertThat(rowCount("account")).isEqualTo(2);
		assertThat(rowCount("customer_accountxref")).isEqualTo(2);
	}

	private long createCustomer() {
		long number = uniqueNumber();
		assertThat(postJson("/customers/add", customerPayload(number, "Jane", "Doe"))
				.getStatusCode().value()).isEqualTo(201);
		return number;
	}

	private void assertProblem(ResponseEntity<String> response, int status, String path) {
		assertThat(response.getStatusCode().value()).isEqualTo(status);
		assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(response.getHeaders().getLocation()).isNull();
		assertThat(json(response).get("status").asInt()).isEqualTo(status);
		assertThat(json(response).get("type").asText()).isEqualTo("about:blank");
		assertThat(json(response).get("title").asText()).isNotBlank();
		assertThat(json(response).get("detail").asText()).isNotBlank();
		assertThat(json(response).get("instance").asText()).isEqualTo(CONTEXT_PATH + path);
	}

	private void assertNoAccounts() {
		assertThat(rowCount("account")).isZero();
		assertThat(rowCount("customer_accountxref")).isZero();
	}

	private static void assertV1Created(ResponseEntity<String> response) {
		assertThat(response.getStatusCode().value()).isEqualTo(201);
		assertThat(response.getBody()).isEqualTo("New Account created successfully.");
		assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_PLAIN)).isTrue();
		assertThat(response.getHeaders().getLocation()).isNull();
	}

	private static void assertResponseValid(HttpMethod method, String path, String body, ResponseEntity<String> response) {
		// These cases deliberately violate the request schema; still validate every response against the spec.
		var report = SPEC.validate(method, path, body, response);
		assertThat(report.getMessages()).filteredOn(message -> !message.getKey().startsWith("validation.request."))
				.isEmpty();
	}
}
