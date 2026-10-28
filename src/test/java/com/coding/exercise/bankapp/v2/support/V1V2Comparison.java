package com.coding.exercise.bankapp.v2.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class V1V2Comparison {

	private final TestRestTemplate rest;
	private Request v1Request;
	private Request v2Request;
	private final List<String> ignoredPointers = new ArrayList<>();
	private final List<String> ignoredFields = new ArrayList<>();

	private V1V2Comparison(TestRestTemplate rest) {
		this.rest = Objects.requireNonNull(rest, "rest");
	}

	public static V1V2Comparison using(TestRestTemplate rest) {
		return new V1V2Comparison(rest);
	}

	public V1V2Comparison v1(HttpMethod method, String path) {
		return v1(method, path, null);
	}

	public V1V2Comparison v1(HttpMethod method, String path, Object requestBody) {
		v1Request = new Request(method, path, requestBody);
		return this;
	}

	public V1V2Comparison v2(HttpMethod method, String path) {
		return v2(method, path, null);
	}

	public V1V2Comparison v2(HttpMethod method, String path, Object requestBody) {
		v2Request = new Request(method, path, requestBody);
		return this;
	}

	public V1V2Comparison ignoringPointers(String... pointers) {
		ignoredPointers.addAll(Arrays.asList(pointers));
		return this;
	}

	public V1V2Comparison ignoringFields(String... fields) {
		ignoredFields.addAll(Arrays.asList(fields));
		return this;
	}

	public Result assertSameJson() {
		Result result = execute();
		List<String> differences = JsonComparison.differences(
				result.v1().getBody(), result.v2().getBody(), ignoredPointers, ignoredFields);
		if (!differences.isEmpty()) {
			throw new AssertionError("V1 " + v1Request.line() + " returned " + result.v1().getStatusCode().value()
					+ System.lineSeparator()
					+ "V2 " + v2Request.line() + " returned " + result.v2().getStatusCode().value()
					+ System.lineSeparator() + String.join(System.lineSeparator(), differences));
		}
		return result;
	}

	public Result execute() {
		if (v1Request == null || v2Request == null) {
			throw new IllegalStateException("Both V1 and V2 requests must be configured before execution");
		}
		ResponseEntity<String> v1Response = send(v1Request);
		ResponseEntity<String> v2Response = send(v2Request);
		return new Result(v1Response, v2Response);
	}

	private ResponseEntity<String> send(Request request) {
		HttpEntity<?> entity = HttpEntity.EMPTY;
		if (request.body() != null) {
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			entity = new HttpEntity<>(request.body(), headers);
		}
		return rest.exchange(request.path(), request.method(), entity, String.class);
	}

	private record Request(HttpMethod method, String path, Object body) {
		private Request {
			Objects.requireNonNull(method, "method");
			Objects.requireNonNull(path, "path");
		}

		private String line() {
			return method.name() + " " + path;
		}
	}

	public record Result(ResponseEntity<String> v1, ResponseEntity<String> v2) {
	}
}
