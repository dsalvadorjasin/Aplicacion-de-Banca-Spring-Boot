package com.coding.exercise.bankapp.v2.accounts;

import java.net.URI;

import com.coding.exercise.bankapp.v2.support.ApiV2Comparison;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:apiv2-accounts-add;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
class AddAccountV2Test extends AccountsV2TestSupport {
    @Test
    void successfulCreationReturnsLocationAccountAndOneCrossReference() throws Exception {
        long customerNumber = id();
        long accountNumber = id();
        long v1AccountNumber = id();
        createCustomer(customerNumber);

        String body = account(accountNumber, "123.45");
        String path = "/v2/accounts/add/" + customerNumber;
        ResponseEntity<String> created = exchange(HttpMethod.POST, path, body);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(created.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        URI location = created.getHeaders().getLocation();
        assertThat(location).isNotNull();
        assertThat(location.isAbsolute()).isTrue();
        assertThat(location.getPath()).isEqualTo("/bank-api/v2/accounts/" + accountNumber);
        assertValidV2(HttpMethod.POST, path, body, created);

        ResponseEntity<String> readFromLocation = ApiV2Comparison.exchange(
                http, HttpMethod.GET, location.toString(), null);
        assertThat(readFromLocation.getStatusCode().value()).isEqualTo(200);
        assertValidV2(HttpMethod.GET, "/v2/accounts/" + accountNumber, null, readFromLocation);
        ApiV2Comparison.assertJsonEquals(created.getBody(), readFromLocation.getBody());
        assertThat(xrefCount(customerNumber, accountNumber)).isEqualTo(1);

        ApiV2Comparison.Responses accountRead = ApiV2Comparison.compare(
                http, HttpMethod.GET, "/accounts/" + accountNumber,
                "/v2/accounts/" + accountNumber, null);
        assertThat(accountRead.v1().getStatusCode().value()).isEqualTo(302);
        assertThat(accountRead.v2().getStatusCode().value()).isEqualTo(200);
        assertValidV2(HttpMethod.GET, "/v2/accounts/" + accountNumber, null, accountRead.v2());

        ResponseEntity<String> v1Created = createV1Account(customerNumber, v1AccountNumber, "123.45");
        assertThat(v1Created.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_PLAIN)).isTrue();
        assertThat(v1Created.getHeaders().getLocation()).isNull();
        assertThat(v1Created.getBody()).isEqualTo("New Account created successfully.");
        ResponseEntity<String> v1Read = exchange(HttpMethod.GET, "/accounts/" + v1AccountNumber, null);
        assertThat(v1Read.getStatusCode().value()).isEqualTo(302);
        ApiV2Comparison.assertJsonEquals(v1Read.getBody(), created.getBody(), "accountNumber");
        assertThat(xrefCount(customerNumber, v1AccountNumber)).isEqualTo(1);
    }

    @Test
    void missingCustomerReturnsNotFoundAndDoesNotCreateAnything() throws Exception {
        long customerNumber = id();
        long accountNumber = id();
        String body = account(accountNumber, "5");
        ResponseEntity<String> v1 = createV1Account(customerNumber, accountNumber, "5");

        ResponseEntity<String> v2 = exchange(
                HttpMethod.POST, "/v2/accounts/add/" + customerNumber, body);
        assertThat(v1.getBody()).isEqualTo("New Account created successfully.");
        assertProblem(v2, 404, "Customer Number " + customerNumber + " not found.",
                "/v2/accounts/add/" + customerNumber);
        assertInvalidV2(HttpMethod.POST, "/v2/accounts/add/" + customerNumber, v2);
        ResponseEntity<String> v1Read = exchange(HttpMethod.GET, "/accounts/" + accountNumber, null);
        assertThat(v1Read.getStatusCode().value()).isEqualTo(404);
        assertThat(v1Read.getBody()).isEqualTo("Account Number " + accountNumber + " not found.");
        assertThat(xrefCount(customerNumber, accountNumber)).isZero();
    }

    @Test
    void duplicateAccountNumberConflictsWithoutChangingOriginalV1Data() throws Exception {
        long customerNumber = id();
        long accountNumber = id();
        long v1Duplicate = id();
        createCustomer(customerNumber);
        createV1Account(customerNumber, accountNumber, "31");

        String body = account(accountNumber, "99");
        ResponseEntity<String> v2 = exchange(
                HttpMethod.POST, "/v2/accounts/add/" + customerNumber, body);
        assertProblem(v2, 409, "Account Number " + accountNumber + " already exists.",
                "/v2/accounts/add/" + customerNumber);
        assertInvalidV2(HttpMethod.POST, "/v2/accounts/add/" + customerNumber, v2);
        ResponseEntity<String> original = exchange(HttpMethod.GET, "/accounts/" + accountNumber, null);
        assertThat(original.getStatusCode().value()).isEqualTo(302);
        assertThat(mapper.readTree(original.getBody()).get("accountBalance").asDouble()).isEqualTo(31);
        assertThat(xrefCount(customerNumber, accountNumber)).isEqualTo(1);

        createV1Account(customerNumber, v1Duplicate, "1");
        createV1Account(customerNumber, v1Duplicate, "2");
        ResponseEntity<String> duplicateRead = exchange(HttpMethod.GET, "/accounts/" + v1Duplicate, null);
        assertBootError(duplicateRead, 500, "/accounts/" + v1Duplicate);
    }

    @Test
    void duplicateStoredCustomersConflictAndCreateNothing() throws Exception {
        long customerNumber = id();
        long accountNumber = id();
        createCustomer(customerNumber);
        createCustomer(customerNumber);
        ResponseEntity<String> v1 = exchange(
                HttpMethod.POST, "/accounts/add/" + customerNumber, account(accountNumber, "1"));
        assertBootError(v1, 500, "/accounts/add/" + customerNumber);

        ResponseEntity<String> v2 = exchange(
                HttpMethod.POST, "/v2/accounts/add/" + customerNumber, account(accountNumber, "1"));
        assertProblem(v2, 409, "Duplicate records found for the requested identifier.",
                "/v2/accounts/add/" + customerNumber);
        assertInvalidV2(HttpMethod.POST, "/v2/accounts/add/" + customerNumber, v2);
        ResponseEntity<String> v1Read = exchange(HttpMethod.GET, "/accounts/" + accountNumber, null);
        assertThat(v1Read.getStatusCode().value()).isEqualTo(404);
        assertThat(xrefCount(customerNumber, accountNumber)).isZero();
    }

    @Test
    void missingAccountFieldsAreRejectedBeforePersistence() throws Exception {
        long customerNumber = id();
        long missingBankAccountNumber = id();
        long missingAddressAccountNumber = id();
        createCustomer(customerNumber);
        long initialXrefs = xrefCountForCustomer(customerNumber);

        String missingNumberBody = accountWithoutNumber("10");
        ResponseEntity<String> missingNumber = exchange(
                HttpMethod.POST, "/v2/accounts/add/" + customerNumber, missingNumberBody);
        assertProblem(missingNumber, 400, "accountNumber is required.", "/v2/accounts/add/" + customerNumber);
        assertInvalidV2(HttpMethod.POST, "/v2/accounts/add/" + customerNumber, missingNumber);
        assertThat(xrefCountForCustomer(customerNumber)).isEqualTo(initialXrefs);

        String missingBankBody = accountWithoutBankInformation(missingBankAccountNumber);
        ResponseEntity<String> missingBank = exchange(
                HttpMethod.POST, "/v2/accounts/add/" + customerNumber, missingBankBody);
        assertProblem(missingBank, 400, "bankInformation is required.", "/v2/accounts/add/" + customerNumber);
        assertInvalidV2(HttpMethod.POST, "/v2/accounts/add/" + customerNumber, missingBank);
        assertAccountNotFound(missingBankAccountNumber);
        assertThat(xrefCountForCustomer(customerNumber)).isEqualTo(initialXrefs);

        String missingAddressBody = accountWithoutBranchAddress(missingAddressAccountNumber);
        ResponseEntity<String> missingAddress = exchange(
                HttpMethod.POST, "/v2/accounts/add/" + customerNumber, missingAddressBody);
        assertProblem(missingAddress, 400, "bankInformation.branchAddress is required.",
                "/v2/accounts/add/" + customerNumber);
        assertInvalidV2(HttpMethod.POST, "/v2/accounts/add/" + customerNumber, missingAddress);
        assertAccountNotFound(missingAddressAccountNumber);
        assertThat(xrefCountForCustomer(customerNumber)).isEqualTo(initialXrefs);

        ResponseEntity<String> v1MissingNumber = exchange(
                HttpMethod.POST, "/accounts/add/" + customerNumber, missingNumberBody);
        assertThat(v1MissingNumber.getStatusCode().value()).isEqualTo(201);
        assertThat(v1MissingNumber.getBody()).isEqualTo("New Account created successfully.");
        assertBootError(exchange(HttpMethod.POST, "/accounts/add/" + customerNumber, missingBankBody),
                500, "/accounts/add/" + customerNumber);
        assertBootError(exchange(HttpMethod.POST, "/accounts/add/" + customerNumber, missingAddressBody),
                500, "/accounts/add/" + customerNumber);
    }

    @Test
    void nonFiniteBalancesAreRejectedButV1PersistsInfinity() throws Exception {
        long customerNumber = id();
        long positiveAccountNumber = id();
        long negativeAccountNumber = id();
        long v1AccountNumber = id();
        createCustomer(customerNumber);
        long initialXrefs = xrefCountForCustomer(customerNumber);

        for (long accountNumber : new long[]{positiveAccountNumber, negativeAccountNumber}) {
            String balance = accountNumber == positiveAccountNumber ? "1e309" : "-1e309";
            ResponseEntity<String> v2 = exchange(
                    HttpMethod.POST, "/v2/accounts/add/" + customerNumber, account(accountNumber, balance));
            assertProblem(v2, 400, "accountBalance must be a finite number.",
                    "/v2/accounts/add/" + customerNumber);
            assertInvalidV2(HttpMethod.POST, "/v2/accounts/add/" + customerNumber, v2);
            assertAccountNotFound(accountNumber);
            assertThat(xrefCountForCustomer(customerNumber)).isEqualTo(initialXrefs);
        }

        createV1Account(customerNumber, v1AccountNumber, "1e309");
        ResponseEntity<String> v1Read = exchange(HttpMethod.GET, "/accounts/" + v1AccountNumber, null);
        assertThat(v1Read.getStatusCode().value()).isEqualTo(302);
        assertThat(v1Read.getBody()).contains("\"accountBalance\":\"Infinity\"");
        assertThat(mapper.readTree(v1Read.getBody()).get("accountBalance").asText()).isEqualTo("Infinity");
    }

    @Test
    void malformedWrongTypeUnsupportedContentTypeAndInvalidPathUseProblemDetail() throws Exception {
        long customerNumber = id();
        createCustomer(customerNumber);
        String path = "/v2/accounts/add/" + customerNumber;
        String v1Path = "/accounts/add/" + customerNumber;
        String[] bodies = {"{", "", "[]", "{\"accountNumber\":\"bad\"}"};

        for (String body : bodies) {
            ResponseEntity<String> v1 = exchange(HttpMethod.POST, v1Path, body);
            assertBootError(v1, 400, v1Path);
            ResponseEntity<String> v2 = exchange(HttpMethod.POST, path, body);
            assertProblem(v2, 400, null, path);
            assertInvalidV2(HttpMethod.POST, path, v2);
        }

        String validBody = account(id(), "1");
        ResponseEntity<String> v1PathError = exchange(HttpMethod.POST, "/accounts/add/nope", validBody);
        assertBootError(v1PathError, 400, "/accounts/add/nope");
        ResponseEntity<String> v2PathError = exchange(HttpMethod.POST, "/v2/accounts/add/nope", validBody);
        assertProblem(v2PathError, 400, null, "/v2/accounts/add/nope");
        assertInvalidV2(HttpMethod.POST, "/v2/accounts/add/nope", v2PathError);

        ResponseEntity<String> v1Unsupported = exchangeWithContentType(
                HttpMethod.POST, v1Path, validBody, MediaType.TEXT_PLAIN);
        assertBootError(v1Unsupported, 415, v1Path);
        ResponseEntity<String> v2Unsupported = exchangeWithContentType(
                HttpMethod.POST, path, validBody, MediaType.TEXT_PLAIN);
        assertProblem(v2Unsupported, 415, null, path);
        assertInvalidV2(HttpMethod.POST, path, v2Unsupported);
    }

    private void assertAccountNotFound(long accountNumber) {
        ResponseEntity<String> v1Read = exchange(HttpMethod.GET, "/accounts/" + accountNumber, null);
        assertThat(v1Read.getStatusCode().value()).isEqualTo(404);
        assertThat(v1Read.getBody()).isEqualTo("Account Number " + accountNumber + " not found.");
    }
}
