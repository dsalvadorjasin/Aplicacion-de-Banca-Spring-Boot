package com.coding.exercise.bankapp.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;

class ApiDocsE2ETest extends E2ETestSupport {

	@Test
	void openApiDocumentIsPublishedWithoutCredentials() {
		ResponseEntity<String> response = get("/v3/api-docs");

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
		JsonNode doc = json(response);
		assertThat(doc.get("openapi").asText()).startsWith("3.");
		assertThat(doc.get("info").get("title").asText()).isEqualTo("BANKING APPLICATION REST API");
		assertThat(doc.get("info").get("description").asText()).isEqualTo("API for Banking Application.");
		assertThat(doc.get("info").get("version").asText()).isEqualTo("1.0.0");
		assertThat(doc.get("servers").get(0).get("url").asText()).endsWith("/bank-api");
	}

	@Test
	void openApiDocumentListsExactlyTheBankingOperations() {
		JsonNode paths = json(get("/v3/api-docs")).get("paths");

		List<String> pathNames = new ArrayList<>();
		paths.fieldNames().forEachRemaining(pathNames::add);
		assertThat(pathNames).containsExactlyInAnyOrder(
				"/customers/all",
				"/customers/add",
				"/customers/{customerNumber}",
				"/accounts/{accountNumber}",
				"/accounts/add/{customerNumber}",
				"/accounts/transfer/{customerNumber}",
				"/accounts/transactions/{accountNumber}");
		assertThat(methods(paths.get("/customers/all"))).containsExactly("get");
		assertThat(methods(paths.get("/customers/add"))).containsExactly("post");
		assertThat(methods(paths.get("/customers/{customerNumber}"))).containsExactlyInAnyOrder("get", "put", "delete");
		assertThat(methods(paths.get("/accounts/{accountNumber}"))).containsExactly("get");
		assertThat(methods(paths.get("/accounts/add/{customerNumber}"))).containsExactly("post");
		assertThat(methods(paths.get("/accounts/transfer/{customerNumber}"))).containsExactly("put");
		assertThat(methods(paths.get("/accounts/transactions/{accountNumber}"))).containsExactly("get");
	}

	@Test
	void openApiDocumentCarriesOperationSummariesAndTags() {
		JsonNode paths = json(get("/v3/api-docs")).get("paths");

		JsonNode transfer = paths.get("/accounts/transfer/{customerNumber}").get("put");
		assertThat(transfer.get("summary").asText()).isEqualTo("Transfer funds between accounts");
		assertThat(transfer.get("tags").get(0).asText()).isEqualTo("Accounts and Transactions REST endpoints");
		JsonNode addCustomer = paths.get("/customers/add").get("post");
		assertThat(addCustomer.get("summary").asText()).isEqualTo("Add a Customer");
		assertThat(addCustomer.get("tags").get(0).asText()).isEqualTo("Customer REST endpoints");
		assertThat(addCustomer.get("responses").has("201")).isFalse();
		assertThat(addCustomer.get("responses").has("200")).isTrue();
	}

	@Test
	void swaggerUiHtmlRedirectsToIndexPage() {
		ResponseEntity<String> response = nonRedirectingClient().getForEntity(url("/swagger-ui.html"), String.class);

		assertThat(response.getStatusCode().value()).isEqualTo(302);
		assertThat(response.getHeaders().getLocation().toString()).endsWith("/bank-api/swagger-ui/index.html");
	}

	@Test
	void swaggerUiIndexIsServedWithoutCredentials() {
		ResponseEntity<String> response = nonRedirectingClient().getForEntity(url("/swagger-ui/index.html"), String.class);

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_HTML)).isTrue();
		assertThat(response.getBody()).contains("<title>Swagger UI</title>");
	}

	@Test
	void swaggerUiConfigPointsAtApiDocs() {
		ResponseEntity<String> response = get("/v3/api-docs/swagger-config");

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(json(response).get("url").asText()).isEqualTo("/bank-api/v3/api-docs");
	}

	private static List<String> methods(JsonNode pathItem) {
		List<String> methods = new ArrayList<>();
		pathItem.fieldNames().forEachRemaining(methods::add);
		return methods;
	}
}
