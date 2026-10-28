package com.coding.exercise.bankapp.v2.moneymovement;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MoneyMovementOpenApiConfig {

	@Bean
	public OpenApiCustomizer preserveDefaultMoneyMovementDocumentation() {
		return openApi -> {
			if (openApi.getPaths() != null) {
				openApi.getPaths().remove("/v2/accounts/transfer/{customerNumber}");
				openApi.getPaths().remove("/v2/accounts/transactions/{accountNumber}");
			}
		};
	}
}
