package com.coding.exercise.bankapp.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

final class ResponseEntityAssertions {

	private ResponseEntityAssertions() {
	}

	static void assertStatusAndBody(ResponseEntity<String> response, HttpStatus status, String body) {
		assertThat(response.getStatusCode().value()).as("status, body=%s", response.getBody()).isEqualTo(status.value());
		assertThat(response.getBody()).isEqualTo(body);
	}

	static void assertCreated(ResponseEntity<String> response, String body) {
		assertStatusAndBody(response, HttpStatus.CREATED, body);
	}

	static void assertEmptyOk(ResponseEntity<String> response) {
		assertThat(response.getStatusCode().value()).as("status, body=%s", response.getBody()).isEqualTo(200);
		assertThat(response.getBody()).isNullOrEmpty();
	}

	static void assertServerError(ResponseEntity<String> response, String path) {
		assertThat(response.getStatusCode().value()).as("status, body=%s", response.getBody()).isEqualTo(500);
		assertThat(response.getBody()).contains("\"status\":500").contains("\"path\":\"" + path + "\"");
	}
}
