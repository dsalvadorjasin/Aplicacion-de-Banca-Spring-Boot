package com.coding.exercise.bankapp.v2.accounts;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.coding.exercise.bankapp.v2.ApiV2;

@Configuration
public class AccountsV2OpenApiConfig {

	@Bean
	public OpenApiCustomizer preserveLegacyAccountsDocument() {
		// A non-global customizer affects only the ungrouped legacy document, not the V2 group.
		return openApi -> {
			if (openApi.getPaths() != null) {
				openApi.getPaths().keySet().removeIf(path -> path.startsWith(ApiV2.BASE_PATH + "/accounts/"));
			}
		};
	}
}
