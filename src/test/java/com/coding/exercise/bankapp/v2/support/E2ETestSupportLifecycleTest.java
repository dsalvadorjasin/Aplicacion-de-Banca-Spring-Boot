package com.coding.exercise.bankapp.v2.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.springframework.http.ResponseEntity;

import com.coding.exercise.bankapp.e2e.E2ETestSupport;

@TestMethodOrder(OrderAnnotation.class)
class E2ETestSupportLifecycleTest extends E2ETestSupport {

	@Test
	@Order(1)
	void createsDataForTheNextTest() {
		ResponseEntity<String> response = postJson(
				"/customers/add", customerPayload(uniqueNumber(), "Lifecycle", "Probe"));

		assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(rowCount("customer")).isPositive();
	}

	@Test
	@Order(2)
	void inheritedPackagePrivateBeforeEachCleansTheDatabase() {
		assertThat(rowCount("customer")).isZero();
	}
}
