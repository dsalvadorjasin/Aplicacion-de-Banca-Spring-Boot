package com.coding.exercise.bankapp.v2.moneymovement;

import com.coding.exercise.bankapp.v2.support.ApiV2Comparison;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:money-movement-v2;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
class TransactionsV2Test {
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;

    private MoneyMovementTestData data;

    @BeforeEach
    void setUp() {
        data = new MoneyMovementTestData(http, mapper);
    }

    @Test
    void transactionsMatchV1IncludingTheDateRepresentationAndEmptyLists() throws Exception {
        long customer = data.customer();
        long from = data.account(customer, 100.0);
        long through = data.account(customer, 25.0);
        long to = data.account(customer, 10.0);
        assertThat(data.exchange(HttpMethod.PUT, "/accounts/transfer/" + customer,
                data.transferBody(from, through, 20.0)).getStatusCode().value()).isEqualTo(200);
        assertThat(data.exchange(HttpMethod.PUT, "/accounts/transfer/" + customer,
                data.transferBody(through, to, 5.0)).getStatusCode().value()).isEqualTo(200);

        String v1Path = "/accounts/transactions/" + through;
        String v2Path = "/v2/accounts/transactions/" + through;
        ApiV2Comparison.Responses responses = ApiV2Comparison.compare(
                http, HttpMethod.GET, v1Path, v2Path, null);
        assertThat(responses.v1().getStatusCode().value()).isEqualTo(200);
        assertThat(responses.v2().getStatusCode().value()).isEqualTo(200);
        assertThat(responses.v2().getHeaders().getContentType()).isNotNull();
        assertThat(responses.v2().getHeaders().getContentType()
                .isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        data.validateV2(HttpMethod.GET, v2Path, null, responses.v2());
        assertThat(data.json(responses.v2())).hasSize(2);
        assertThat(data.json(responses.v1()).get(0).get("txDateTime").isTextual()).isTrue();

        long withoutTransactions = data.account(customer, 7.0);
        ApiV2Comparison.Responses empty = ApiV2Comparison.compare(
                http,
                HttpMethod.GET,
                "/accounts/transactions/" + withoutTransactions,
                "/v2/accounts/transactions/" + withoutTransactions,
                null);
        assertThat(empty.v1().getStatusCode().value()).isEqualTo(200);
        assertThat(empty.v2().getStatusCode().value()).isEqualTo(200);
        assertThat(empty.v1().getBody()).isEqualTo("[]");
        assertThat(empty.v2().getBody()).isEqualTo("[]");
        data.validateV2(
                HttpMethod.GET,
                "/v2/accounts/transactions/" + withoutTransactions,
                null,
                empty.v2());
    }

    @Test
    void missingAccountIsAProblemInV2ButAnEmptyListInV1() throws Exception {
        long missing = data.id();
        ResponseEntity<String> v1 = data.exchange(HttpMethod.GET, "/accounts/transactions/" + missing, null);
        assertThat(v1.getStatusCode().value()).isEqualTo(200);
        assertThat(v1.getBody()).isEqualTo("[]");

        String path = "/v2/accounts/transactions/" + missing;
        ResponseEntity<String> v2 = data.v2Valid(HttpMethod.GET, path, null);
        data.assertProblem(v2, 404, "Account Number " + missing + " not found.", path);
    }

    @Test
    void duplicateAccountNumbersAreAConflictInV2() throws Exception {
        long customer = data.customer();
        long duplicate = data.id();
        data.createAccount(customer, duplicate, 100.0);
        data.createAccount(customer, duplicate, 200.0);

        String v1Path = "/accounts/transactions/" + duplicate;
        ResponseEntity<String> v1 = data.exchange(HttpMethod.GET, v1Path, null);
        data.assertBootError(v1, 500, v1Path);

        String v2Path = "/v2/accounts/transactions/" + duplicate;
        ResponseEntity<String> v2 = data.v2Valid(HttpMethod.GET, v2Path, null);
        data.assertProblem(v2, 409, "Duplicate records found for the requested identifier.", v2Path);
    }

    @Test
    void malformedAndOutOfRangeAccountNumbersAreProblemDetailsInV2() throws Exception {
        for (String invalid : new String[] {"nope", "99999999999999999999"}) {
            String v1Path = "/accounts/transactions/" + invalid;
            ResponseEntity<String> v1 = data.exchange(HttpMethod.GET, v1Path, null);
            data.assertBootError(v1, 400, v1Path);

            String v2Path = "/v2/accounts/transactions/" + invalid;
            ResponseEntity<String> v2 = data.v2Invalid(HttpMethod.GET, v2Path, null);
            assertThat(v2.getStatusCode().value()).isEqualTo(400);
            data.assertProblemHasNoBootFields(v2);
            data.assertProblem(v2, 400, null, v2Path);
        }
    }
}
