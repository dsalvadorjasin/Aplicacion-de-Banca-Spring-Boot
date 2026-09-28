package com.coding.exercise.bankapp.e2e;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Base class for end-to-end tests: boots the full application on a random port
 * and drives it over real HTTP (servlet container + Spring Security filter chain).
 * All tables are wiped before every test so tests are independent of execution order.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class E2ETestSupport {

	protected static final String CONTEXT_PATH = "/bank-api";

	private static final AtomicLong NUMBER_SEQUENCE = new AtomicLong(100_000L);

	private static final List<String> TABLES_IN_DELETE_ORDER = Arrays.asList(
			"customer_accountxref", "transaction", "account", "bank_info", "customer", "contact", "address");

	@Autowired
	protected TestRestTemplate rest;

	@Autowired
	protected JdbcTemplate jdbcTemplate;

	@Autowired
	protected ObjectMapper objectMapper;

	@LocalServerPort
	protected int port;

	@BeforeEach
	void cleanDatabase() {
		TABLES_IN_DELETE_ORDER.forEach(table -> jdbcTemplate.update("DELETE FROM " + table));
	}

	protected static long uniqueNumber() {
		return NUMBER_SEQUENCE.incrementAndGet();
	}

	protected ResponseEntity<String> get(String path) {
		return rest.getForEntity(path, String.class);
	}

	protected ResponseEntity<String> postJson(String path, Object body) {
		return exchangeJson(HttpMethod.POST, path, body);
	}

	protected ResponseEntity<String> putJson(String path, Object body) {
		return exchangeJson(HttpMethod.PUT, path, body);
	}

	protected ResponseEntity<String> delete(String path) {
		return rest.exchange(path, HttpMethod.DELETE, HttpEntity.EMPTY, String.class);
	}

	protected ResponseEntity<String> exchangeJson(HttpMethod method, String path, Object body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return rest.exchange(path, method, new HttpEntity<>(body, headers), String.class);
	}

	protected JsonNode json(ResponseEntity<String> response) {
		try {
			return objectMapper.readTree(response.getBody());
		} catch (IOException e) {
			throw new IllegalStateException("Response body is not JSON: " + response.getBody(), e);
		}
	}

	protected int rowCount(String table) {
		Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
		return count == null ? 0 : count;
	}

	protected String url(String path) {
		return "http://localhost:" + port + CONTEXT_PATH + path;
	}

	/**
	 * A plain RestTemplate that never follows redirects and never throws on 4xx/5xx,
	 * so 3xx responses and their Location headers can be asserted directly.
	 */
	protected RestTemplate nonRedirectingClient() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
			@Override
			protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
				super.prepareConnection(connection, httpMethod);
				connection.setInstanceFollowRedirects(false);
			}
		};
		RestTemplate client = new RestTemplate(factory);
		client.setErrorHandler(new DefaultResponseErrorHandler() {
			@Override
			public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
				return false;
			}
		});
		return client;
	}

	protected static Map<String, Object> customerPayload(long customerNumber, String firstName, String lastName) {
		Map<String, Object> contact = new LinkedHashMap<>();
		contact.put("emailId", firstName.toLowerCase() + "@test.com");
		contact.put("homePhone", "6150000000");
		contact.put("workPhone", "6151112222");

		Map<String, Object> customer = new LinkedHashMap<>();
		customer.put("customerNumber", customerNumber);
		customer.put("firstName", firstName);
		customer.put("middleName", "H");
		customer.put("lastName", lastName);
		customer.put("status", "Active");
		customer.put("contactDetails", contact);
		customer.put("customerAddress", address("123 Domain St", "Hermitage", "TN", "37076"));
		return customer;
	}

	protected static Map<String, Object> accountPayload(long accountNumber, double balance, String accountType) {
		Map<String, Object> bankInformation = new LinkedHashMap<>();
		bankInformation.put("branchName", "Nashville Shores");
		bankInformation.put("branchCode", 65564);
		bankInformation.put("routingNumber", 234789876);
		bankInformation.put("branchAddress", address("500 Branch Rd", "Nashville", "TN", "37201"));

		Map<String, Object> account = new LinkedHashMap<>();
		account.put("accountNumber", accountNumber);
		account.put("accountBalance", balance);
		account.put("accountStatus", "Active");
		account.put("accountType", accountType);
		account.put("accountCreated", "2019-05-05T22:01:05.964Z");
		account.put("bankInformation", bankInformation);
		return account;
	}

	protected static Map<String, Object> transferPayload(long fromAccountNumber, long toAccountNumber, double amount) {
		Map<String, Object> transfer = new LinkedHashMap<>();
		transfer.put("fromAccountNumber", fromAccountNumber);
		transfer.put("toAccountNumber", toAccountNumber);
		transfer.put("transferAmount", amount);
		return transfer;
	}

	protected static Map<String, Object> address(String address1, String city, String state, String zip) {
		Map<String, Object> address = new LinkedHashMap<>();
		address.put("address1", address1);
		address.put("address2", "Suite D");
		address.put("city", city);
		address.put("state", state);
		address.put("zip", zip);
		address.put("country", "USA");
		return address;
	}
}
