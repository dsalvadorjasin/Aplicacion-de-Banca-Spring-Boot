package com.coding.exercise.bankapp.v2;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.servlet.ServletContext;

import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.properties.SwaggerUiConfigParameters;
import org.springframework.boot.ApplicationRunner;

@Configuration
public class ApiV2OpenApiConfig {

	@Bean
	public GroupedOpenApi v1OpenApi() {
		return GroupedOpenApi.builder()
				.group("v1")
				.pathsToExclude("/v2/**")
				.build();
	}

	@Bean
	public GroupedOpenApi v2OpenApi() {
		return GroupedOpenApi.builder()
				.group("v2")
				.pathsToMatch("/v2/**")
				.build();
	}

	@Bean
	public ApplicationRunner preserveDefaultSwaggerUrl(SwaggerUiConfigParameters parameters,
			ServletContext servletContext) {
		return args -> {
			if (parameters.getUrl() == null) {
				parameters.setUrl(servletContext.getContextPath() + "/v3/api-docs");
			}
		};
	}
}
