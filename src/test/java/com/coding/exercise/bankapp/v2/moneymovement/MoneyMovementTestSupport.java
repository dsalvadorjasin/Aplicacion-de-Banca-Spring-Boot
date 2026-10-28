package com.coding.exercise.bankapp.v2.moneymovement;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.test.context.TestPropertySource;

import com.coding.exercise.bankapp.v2.support.ApiV2TestSupport;

@TestPropertySource(properties = {
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.sql.init.mode=always",
		"spring.sql.init.schema-locations=classpath:e2e/uuid-schema.sql" })
abstract class MoneyMovementTestSupport extends ApiV2TestSupport {

	protected long customer() {
		long number = uniqueNumber();
		assertThat(postJson("/customers/add", customerPayload(number, "Money", "Movement"))
				.getStatusCode().value()).isEqualTo(201);
		return number;
	}

	protected long account(long customer, double balance) {
		long number = uniqueNumber();
		assertThat(postJson("/accounts/add/" + customer, accountPayload(number, balance, "Checking"))
				.getStatusCode().value()).isEqualTo(201);
		return number;
	}

	protected double balance(long account) {
		return json(get("/accounts/" + account)).get("accountBalance").asDouble();
	}
}
