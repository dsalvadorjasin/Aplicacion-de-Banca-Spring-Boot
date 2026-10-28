package com.coding.exercise.bankapp.v2.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

class V1V2ComparisonTest extends ApiV2TestSupport {

	@Test
	void comparesAnExistingV1ReadWithItself() {
		V1V2Comparison.Result result = compare()
				.v1(HttpMethod.GET, "/customers/all")
				.v2(HttpMethod.GET, "/customers/all")
				.assertSameJson();

		assertThat(result.v1().getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.v2().getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void includesRequestsStatusesAndDifferencePointersOnFailure() {
		TestRestTemplate fakeRest = stubRest(
				ResponseEntity.ok("{\"name\":\"V1\"}"),
				ResponseEntity.status(HttpStatus.CONFLICT).body("{\"name\":\"V2\"}"));

		Throwable failure = catchThrowable(() -> V1V2Comparison.using(fakeRest)
				.v1(HttpMethod.GET, "/customers/1")
				.v2(HttpMethod.GET, "/v2/customers/1")
				.assertSameJson());

		assertThat(failure)
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("GET /customers/1 returned 200")
				.hasMessageContaining("GET /v2/customers/1 returned 409")
				.hasMessageContaining("at /name");
	}

	@Test
	void ignoresDifferencesDeclaredByPointerOrFieldName() {
		TestRestTemplate fakeRest = stubRest(
				ResponseEntity.ok("{\"profile\":{\"version\":1,\"volatile\":\"old\"}}"),
				ResponseEntity.ok("{\"profile\":{\"version\":2,\"volatile\":\"new\"}}"));

		V1V2Comparison.Result result = V1V2Comparison.using(fakeRest)
				.v1(HttpMethod.GET, "/customers/1")
				.v2(HttpMethod.GET, "/v2/customers/1")
				.ignoringPointers("/profile/version")
				.ignoringFields("volatile")
				.assertSameJson();

		assertThat(result.v1().getBody()).contains("\"version\":1");
	}

	private static TestRestTemplate stubRest(ResponseEntity<String> first, ResponseEntity<String> second) {
		return new StubTestRestTemplate(first, second);
	}

	private static class StubTestRestTemplate extends TestRestTemplate {

		private final Deque<ResponseEntity<String>> responses;

		private StubTestRestTemplate(ResponseEntity<String> first, ResponseEntity<String> second) {
			responses = new ArrayDeque<>(List.of(first, second));
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> ResponseEntity<T> exchange(String url, HttpMethod method, HttpEntity<?> requestEntity,
				Class<T> responseType, Object... uriVariables) {
			return (ResponseEntity<T>) responses.removeFirst();
		}
	}
}
