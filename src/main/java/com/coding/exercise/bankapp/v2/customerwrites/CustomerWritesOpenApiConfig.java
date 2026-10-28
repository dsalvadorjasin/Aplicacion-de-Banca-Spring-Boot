package com.coding.exercise.bankapp.v2.customerwrites;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.coding.exercise.bankapp.v2.ApiV2;

@Configuration
class CustomerWritesOpenApiConfig {

	@Bean
	OpenApiCustomizer customerWritesExcludeFromDefaultOpenApi() {
		return openApi -> {
			if (openApi.getPaths() != null) {
				openApi.getPaths().keySet().removeIf(path -> path.startsWith(ApiV2.BASE_PATH + "/customers/"));
			}
		};
	}
}
