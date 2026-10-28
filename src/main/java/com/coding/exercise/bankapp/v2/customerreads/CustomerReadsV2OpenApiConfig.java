package com.coding.exercise.bankapp.v2.customerreads;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.coding.exercise.bankapp.v2.ApiV2;

@Configuration
public class CustomerReadsV2OpenApiConfig {

	@Bean
	public OpenApiCustomizer excludeCustomerReadsV2FromDefaultOpenApi() {
		return openApi -> {
			if (openApi.getPaths() != null) {
				openApi.getPaths().keySet().removeIf(path -> path.startsWith(ApiV2.BASE_PATH + "/customers/"));
			}
		};
	}
}
