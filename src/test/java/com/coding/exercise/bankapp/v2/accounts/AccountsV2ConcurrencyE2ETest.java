package com.coding.exercise.bankapp.v2.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import com.coding.exercise.bankapp.domain.AccountInformation;
import com.coding.exercise.bankapp.service.BankingServiceImpl;
import com.coding.exercise.bankapp.v2.support.ApiV2SpecValidator;
import com.coding.exercise.bankapp.v2.support.ApiV2TestSupport;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountsV2ConcurrencyE2ETest extends ApiV2TestSupport {

	private static final ApiV2SpecValidator SPEC = spec("accounts");

	@SpyBean
	private BankingServiceImpl bankingService;

	@Test
	void legacyInsertBetweenDuplicateCheckAndWriteRollsBackV2AndReturns409() throws Exception {
		long customer = uniqueNumber();
		assertThat(postJson("/customers/add", customerPayload(customer, "Race", "Test"))
				.getStatusCode().value()).isEqualTo(201);
		long number = uniqueNumber();
		var payload = accountPayload(number, 10.0, "Saving");
		var inserted = new AtomicBoolean();
		doAnswer(invocation -> {
			if (inserted.compareAndSet(false, true)) {
				var legacy = postJson("/accounts/add/" + customer, payload);
				assertThat(legacy.getStatusCode().value()).isEqualTo(201);
				assertThat(legacy.getBody()).isEqualTo("New Account created successfully.");
			}
			return invocation.callRealMethod();
		}).when(bankingService).addNewAccount(any(AccountInformation.class), eq(customer));

		String path = "/v2/accounts/add/" + customer;
		var response = postJson(path, payload);
		assertThat(inserted).isTrue();
		assertThat(response.getStatusCode().value()).isEqualTo(409);
		assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(json(response).path("status").asInt()).isEqualTo(409);
		SPEC.assertValid(HttpMethod.POST, path, objectMapper.writeValueAsString(payload), response);
		assertThat(rowCount("account")).isEqualTo(1);
		assertThat(rowCount("customer_accountxref")).isEqualTo(1);
		assertThat(rowCount("bank_info")).isEqualTo(1);
		var read = get("/v2/accounts/" + number);
		assertThat(read.getStatusCode().value()).isEqualTo(200);
		SPEC.assertValid(HttpMethod.GET, "/v2/accounts/" + number, null, read);
	}

	@Test
	void simultaneousV2CreatesCommitOnlyOneAccount() throws Exception {
		long customer = uniqueNumber();
		assertThat(postJson("/customers/add", customerPayload(customer, "Concurrent", "Test"))
				.getStatusCode().value()).isEqualTo(201);
		var payload = accountPayload(uniqueNumber(), 10.0, "Saving");
		String path = "/v2/accounts/add/" + customer;
		var first = CompletableFuture.supplyAsync(() -> postJson(path, payload));
		var second = CompletableFuture.supplyAsync(() -> postJson(path, payload));
		var responses = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
		assertThat(responses).extracting(response -> response.getStatusCode().value())
				.containsExactlyInAnyOrder(201, 409);
		for (var response : responses) {
			SPEC.assertValid(HttpMethod.POST, path, objectMapper.writeValueAsString(payload), response);
		}
		assertThat(rowCount("account")).isEqualTo(1);
		assertThat(rowCount("customer_accountxref")).isEqualTo(1);
	}
}
