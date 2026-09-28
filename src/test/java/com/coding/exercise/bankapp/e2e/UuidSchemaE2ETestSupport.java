package com.coding.exercise.bankapp.e2e;

import org.springframework.test.context.TestPropertySource;

/**
 * Runs the unmodified application against a test-only schema (e2e/uuid-schema.sql) that
 * stores the entity UUID ids in native H2 {@code uuid} columns. Hibernate 6 generates the same
 * column types (see {@link DefaultSchemaBaselineE2ETest}); the explicit schema keeps the
 * functional contract of the API (CRUD, transfers, history) independent of Hibernate DDL generation.
 */
@TestPropertySource(properties = {
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.sql.init.mode=always",
		"spring.sql.init.schema-locations=classpath:e2e/uuid-schema.sql" })
public abstract class UuidSchemaE2ETestSupport extends E2ETestSupport {

	protected void createCustomer(long customerNumber, String firstName, String lastName) {
		ResponseEntityAssertions.assertCreated(postJson("/customers/add", customerPayload(customerNumber, firstName, lastName)),
				"New Customer created successfully.");
	}

	protected void createAccount(long customerNumber, long accountNumber, double balance) {
		ResponseEntityAssertions.assertCreated(
				postJson("/accounts/add/" + customerNumber, accountPayload(accountNumber, balance, "Checking")),
				"New Account created successfully.");
	}

	protected double balanceOf(long accountNumber) {
		return json(get("/accounts/" + accountNumber)).get("accountBalance").asDouble();
	}
}
