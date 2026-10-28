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
class TransferV2Test {
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;

    private MoneyMovementTestData data;

    @BeforeEach
    void setUp() {
        data = new MoneyMovementTestData(http, mapper);
    }

    @Test
    void successfulTransferReturnsBalancesAndMatchesV1AccountAndTransactionData() throws Exception {
        Setup v1Setup = setup(100.0, 20.0);
        Setup v2Setup = setup(100.0, 20.0);

        String v1Path = "/accounts/transfer/" + v1Setup.customer();
        ResponseEntity<String> v1 = data.exchange(
                HttpMethod.PUT, v1Path, data.transferBody(v1Setup.from(), v1Setup.to(), 25.0));
        assertThat(v1.getStatusCode().value()).isEqualTo(200);
        assertThat(v1.getBody()).isEqualTo("Success: Amount transferred for Customer Number "
                + v1Setup.customer());

        String v2Path = "/v2/accounts/transfer/" + v2Setup.customer();
        String request = data.transferBody(v2Setup.from(), v2Setup.to(), 25.0);
        ResponseEntity<String> v2 = data.v2Valid(HttpMethod.PUT, v2Path, request);
        assertThat(v2.getStatusCode().value()).isEqualTo(200);
        assertThat(data.json(v2)).isEqualTo(mapper.readTree("{\"customerNumber\":" + v2Setup.customer()
                + ",\"fromAccountNumber\":" + v2Setup.from()
                + ",\"toAccountNumber\":" + v2Setup.to()
                + ",\"transferAmount\":25.0,\"fromAccountBalance\":75.0,\"toAccountBalance\":45.0}"));

        ApiV2Comparison.assertJsonEquals(
                data.readAccount(v1Setup.from()).getBody(),
                data.readAccount(v2Setup.from()).getBody(),
                "accountNumber");
        ApiV2Comparison.assertJsonEquals(
                data.readAccount(v1Setup.to()).getBody(),
                data.readAccount(v2Setup.to()).getBody(),
                "accountNumber");
        ApiV2Comparison.assertJsonEquals(
                data.transactions(v1Setup.from()).toString(),
                data.transactions(v2Setup.from()).toString(),
                "accountNumber",
                "txDateTime");
        ApiV2Comparison.assertJsonEquals(
                data.transactions(v1Setup.to()).toString(),
                data.transactions(v2Setup.to()).toString(),
                "accountNumber",
                "txDateTime");
    }

    @Test
    void ownershipIsNotEnforcedInEitherVersion() throws Exception {
        Setup v1Setup = setupOwnedByAnotherCustomer(100.0, 20.0);
        Setup v2Setup = setupOwnedByAnotherCustomer(100.0, 20.0);

        ResponseEntity<String> v1 = data.exchange(
                HttpMethod.PUT,
                "/accounts/transfer/" + v1Setup.customer(),
                data.transferBody(v1Setup.from(), v1Setup.to(), 25.0));
        assertThat(v1.getStatusCode().value()).isEqualTo(200);
        assertThat(v1.getBody()).isEqualTo(
                "Success: Amount transferred for Customer Number " + v1Setup.customer());

        String path = "/v2/accounts/transfer/" + v2Setup.customer();
        ResponseEntity<String> v2 = data.v2Valid(
                HttpMethod.PUT, path, data.transferBody(v2Setup.from(), v2Setup.to(), 25.0));
        assertThat(v2.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void sameAccountTransfersPreserveV1BalancesAndRecordBothTransactions() throws Exception {
        long customer = data.customer();
        long account = data.account(customer, 100.0);
        String body = data.transferBody(account, account, 25.0);

        ResponseEntity<String> v1 = data.exchange(
                HttpMethod.PUT, "/accounts/transfer/" + customer, body);
        assertThat(v1.getStatusCode().value()).isEqualTo(200);
        assertThat(v1.getBody()).isEqualTo(
                "Success: Amount transferred for Customer Number " + customer);

        String path = "/v2/accounts/transfer/" + customer;
        ResponseEntity<String> v2 = data.v2Valid(HttpMethod.PUT, path, body);
        assertThat(v2.getStatusCode().value()).isEqualTo(200);
        assertThat(data.json(v2)).isEqualTo(mapper.readTree("{\"customerNumber\":" + customer
                + ",\"fromAccountNumber\":" + account
                + ",\"toAccountNumber\":" + account
                + ",\"transferAmount\":25.0,\"fromAccountBalance\":100.0,\"toAccountBalance\":100.0}"));
        data.assertBalance(account, 100.0);
        assertThat(data.transactions(account)).hasSize(4);
    }

    @Test
    void missingCustomerAndEitherAccountKeepV1MessagesAndUseV2ProblemDetails() throws Exception {
        Setup customerMissing = setup(100.0, 20.0);
        long missingCustomer = data.id();
        String v1CustomerPath = "/accounts/transfer/" + missingCustomer;
        ResponseEntity<String> v1Customer = data.exchange(
                HttpMethod.PUT,
                v1CustomerPath,
                data.transferBody(customerMissing.from(), customerMissing.to(), 1.0));
        assertThat(v1Customer.getStatusCode().value()).isEqualTo(404);
        assertThat(v1Customer.getBody()).isEqualTo("Customer Number " + missingCustomer + " not found.");
        String v2CustomerPath = "/v2/accounts/transfer/" + missingCustomer;
        ResponseEntity<String> v2Customer = data.v2Valid(
                HttpMethod.PUT,
                v2CustomerPath,
                data.transferBody(customerMissing.from(), customerMissing.to(), 1.0));
        data.assertProblem(v2Customer, 404, v1Customer.getBody(), v2CustomerPath);
        assertRejectedState(customerMissing, 100.0, 20.0);

        Setup fromMissing = setup(100.0, 20.0);
        long missingFrom = data.id();
        String v1FromPath = "/accounts/transfer/" + fromMissing.customer();
        ResponseEntity<String> v1From = data.exchange(
                HttpMethod.PUT, v1FromPath, data.transferBody(missingFrom, fromMissing.to(), 1.0));
        assertThat(v1From.getStatusCode().value()).isEqualTo(404);
        assertThat(v1From.getBody()).isEqualTo("From Account Number " + missingFrom + " not found.");
        String v2FromPath = "/v2/accounts/transfer/" + fromMissing.customer();
        ResponseEntity<String> v2From = data.v2Valid(
                HttpMethod.PUT, v2FromPath, data.transferBody(missingFrom, fromMissing.to(), 1.0));
        data.assertProblem(v2From, 404, v1From.getBody(), v2FromPath);
        data.assertBalance(fromMissing.to(), 20.0);
        data.assertNoTransactions(fromMissing.to());

        Setup toMissing = setup(100.0, 20.0);
        long missingTo = data.id();
        String v1ToPath = "/accounts/transfer/" + toMissing.customer();
        ResponseEntity<String> v1To = data.exchange(
                HttpMethod.PUT, v1ToPath, data.transferBody(toMissing.from(), missingTo, 1.0));
        assertThat(v1To.getStatusCode().value()).isEqualTo(404);
        assertThat(v1To.getBody()).isEqualTo("To Account Number " + missingTo + " not found.");
        String v2ToPath = "/v2/accounts/transfer/" + toMissing.customer();
        ResponseEntity<String> v2To = data.v2Valid(
                HttpMethod.PUT, v2ToPath, data.transferBody(toMissing.from(), missingTo, 1.0));
        data.assertProblem(v2To, 404, v1To.getBody(), v2ToPath);
        data.assertBalance(toMissing.from(), 100.0);
        data.assertNoTransactions(toMissing.from());
    }

    @Test
    void insufficientFundsIs422InV2And400InV1() throws Exception {
        Setup setup = setup(100.0, 20.0);
        ResponseEntity<String> v1 = data.exchange(
                HttpMethod.PUT,
                "/accounts/transfer/" + setup.customer(),
                data.transferBody(setup.from(), setup.to(), 101.0));
        assertThat(v1.getStatusCode().value()).isEqualTo(400);
        assertThat(v1.getBody()).isEqualTo("Insufficient Funds.");

        String path = "/v2/accounts/transfer/" + setup.customer();
        ResponseEntity<String> v2 = data.v2Valid(
                HttpMethod.PUT, path, data.transferBody(setup.from(), setup.to(), 101.0));
        data.assertProblem(v2, 422, "Insufficient Funds.", path);
        assertRejectedState(setup, 100.0, 20.0);
    }

    @Test
    void zeroAndNegativeAmountsAreAcceptedByV1ButRejectedWithoutChangesInV2() throws Exception {
        Setup v1Setup = setup(100.0, 20.0);
        ResponseEntity<String> negativeV1 = data.exchange(
                HttpMethod.PUT,
                "/accounts/transfer/" + v1Setup.customer(),
                data.transferBody(v1Setup.from(), v1Setup.to(), -5.0));
        assertThat(negativeV1.getStatusCode().value()).isEqualTo(200);
        assertThat(negativeV1.getBody()).isEqualTo(
                "Success: Amount transferred for Customer Number " + v1Setup.customer());
        data.assertBalance(v1Setup.from(), 105.0);
        data.assertBalance(v1Setup.to(), 15.0);
        ResponseEntity<String> zeroV1 = data.exchange(
                HttpMethod.PUT,
                "/accounts/transfer/" + v1Setup.customer(),
                data.transferBody(v1Setup.from(), v1Setup.to(), 0.0));
        assertThat(zeroV1.getStatusCode().value()).isEqualTo(200);
        assertThat(zeroV1.getBody()).isEqualTo(
                "Success: Amount transferred for Customer Number " + v1Setup.customer());
        data.assertBalance(v1Setup.from(), 105.0);
        data.assertBalance(v1Setup.to(), 15.0);

        for (double amount : new double[] {-5.0, 0.0}) {
            Setup v2Setup = setup(100.0, 20.0);
            String path = "/v2/accounts/transfer/" + v2Setup.customer();
            String request = data.transferBody(v2Setup.from(), v2Setup.to(), amount);
            ResponseEntity<String> v2 = data.v2Invalid(HttpMethod.PUT, path, request);
            data.assertProblem(v2, 400, "transferAmount must be greater than 0.", path);
            assertRejectedState(v2Setup, 100.0, 20.0);
        }
    }

    @Test
    void missingTransferFieldsAre400InV2AndPreserveV1Behavior() throws Exception {
        Setup setup = setup(100.0, 20.0);
        String v1Path = "/accounts/transfer/" + setup.customer();
        String v2Path = "/v2/accounts/transfer/" + setup.customer();

        String missingAmount = data.transferBody(setup.from(), setup.to(), null);
        ResponseEntity<String> v1Amount = data.exchange(HttpMethod.PUT, v1Path, missingAmount);
        assertThat(v1Amount.getStatusCode().value()).isEqualTo(500);
        ResponseEntity<String> v2Amount = data.v2Invalid(HttpMethod.PUT, v2Path, missingAmount);
        data.assertProblem(v2Amount, 400, "transferAmount must not be null.", v2Path);
        assertRejectedState(setup, 100.0, 20.0);

        String missingFrom = data.transferBody(null, setup.to(), 1.0);
        ResponseEntity<String> v1From = data.exchange(HttpMethod.PUT, v1Path, missingFrom);
        assertThat(v1From.getStatusCode().value()).isEqualTo(404);
        assertThat(v1From.getBody()).isEqualTo("From Account Number null not found.");
        ResponseEntity<String> v2From = data.v2Invalid(HttpMethod.PUT, v2Path, missingFrom);
        data.assertProblem(v2From, 400, "fromAccountNumber must not be null.", v2Path);
        assertRejectedState(setup, 100.0, 20.0);

        String missingTo = data.transferBody(setup.from(), null, 1.0);
        ResponseEntity<String> v1To = data.exchange(HttpMethod.PUT, v1Path, missingTo);
        assertThat(v1To.getStatusCode().value()).isEqualTo(404);
        assertThat(v1To.getBody()).isEqualTo("To Account Number null not found.");
        ResponseEntity<String> v2To = data.v2Invalid(HttpMethod.PUT, v2Path, missingTo);
        data.assertProblem(v2To, 400, "toAccountNumber must not be null.", v2Path);
        assertRejectedState(setup, 100.0, 20.0);
    }

    @Test
    void nonFiniteAmountIsRejectedByV2AndV1ReportsInsufficientFunds() throws Exception {
        Setup setup = setup(100.0, 20.0);
        String request = "{\"fromAccountNumber\":" + setup.from()
                + ",\"toAccountNumber\":" + setup.to() + ",\"transferAmount\":1e400}";
        ResponseEntity<String> v1 = data.exchange(
                HttpMethod.PUT, "/accounts/transfer/" + setup.customer(), request);
        assertThat(v1.getStatusCode().value()).isEqualTo(400);
        assertThat(v1.getBody()).isEqualTo("Insufficient Funds.");

        String path = "/v2/accounts/transfer/" + setup.customer();
        ResponseEntity<String> v2 = data.v2Invalid(HttpMethod.PUT, path, request);
        data.assertProblem(v2, 400, "transferAmount must be a finite number.", path);
        assertRejectedState(setup, 100.0, 20.0);
    }

    @Test
    void overflowingBalanceIsAppliedByV1ButRejectedWithoutChangesByV2() throws Exception {
        Setup v1Setup = setup(1.7e308, 1.7e308);
        ResponseEntity<String> v1 = data.exchange(
                HttpMethod.PUT,
                "/accounts/transfer/" + v1Setup.customer(),
                data.transferBody(v1Setup.from(), v1Setup.to(), 1.0e308));
        assertThat(v1.getStatusCode().value()).isEqualTo(200);
        assertThat(v1.getBody()).isEqualTo(
                "Success: Amount transferred for Customer Number " + v1Setup.customer());

        Setup v2Setup = setup(1.7e308, 1.7e308);
        String path = "/v2/accounts/transfer/" + v2Setup.customer();
        ResponseEntity<String> v2 = data.v2Valid(
                HttpMethod.PUT, path, data.transferBody(v2Setup.from(), v2Setup.to(), 1.0e308));
        data.assertProblem(v2, 422, "Transfer would make an account balance non-finite.", path);
        assertRejectedState(v2Setup, 1.7e308, 1.7e308);
    }

    @Test
    void accountsWithoutStoredBalancesAre500InV1And422InV2() throws Exception {
        long customer = data.customer();
        long sourceWithoutBalance = data.id();
        ResponseEntity<String> sourceCreation = data.createAccount(customer, sourceWithoutBalance, null);
        assertThat(sourceCreation.getStatusCode().value()).isEqualTo(201);
        long target = data.account(customer, 100.0);
        ResponseEntity<String> v1Source = data.exchange(
                HttpMethod.PUT,
                "/accounts/transfer/" + customer,
                data.transferBody(sourceWithoutBalance, target, 1.0));
        assertThat(v1Source.getStatusCode().value()).isEqualTo(500);
        String sourcePath = "/v2/accounts/transfer/" + customer;
        ResponseEntity<String> v2Source = data.v2Valid(
                HttpMethod.PUT, sourcePath, data.transferBody(sourceWithoutBalance, target, 1.0));
        data.assertProblem(
                v2Source, 422, "Account Number " + sourceWithoutBalance + " has no valid balance.", sourcePath);
        data.assertBalance(sourceWithoutBalance, null);
        data.assertBalance(target, 100.0);
        data.assertNoTransactions(sourceWithoutBalance, target);

        long targetWithoutBalance = data.id();
        ResponseEntity<String> targetCreation = data.createAccount(customer, targetWithoutBalance, null);
        assertThat(targetCreation.getStatusCode().value()).isEqualTo(201);
        long source = data.account(customer, 100.0);
        ResponseEntity<String> v1Target = data.exchange(
                HttpMethod.PUT,
                "/accounts/transfer/" + customer,
                data.transferBody(source, targetWithoutBalance, 1.0));
        assertThat(v1Target.getStatusCode().value()).isEqualTo(500);
        ResponseEntity<String> v2Target = data.v2Valid(
                HttpMethod.PUT,
                sourcePath,
                data.transferBody(source, targetWithoutBalance, 1.0));
        data.assertProblem(
                v2Target, 422, "Account Number " + targetWithoutBalance + " has no valid balance.", sourcePath);
        data.assertBalance(source, 100.0);
        data.assertBalance(targetWithoutBalance, null);
        data.assertNoTransactions(source, targetWithoutBalance);
    }

    @Test
    void duplicateCustomerAndAccountNumbersAre409InV2And500InV1() throws Exception {
        long duplicatedCustomer = data.customer();
        long customerSource = data.account(duplicatedCustomer, 100.0);
        long customerTarget = data.account(duplicatedCustomer, 20.0);
        data.createCustomer(duplicatedCustomer);
        ResponseEntity<String> v1Customer = data.exchange(
                HttpMethod.PUT,
                "/accounts/transfer/" + duplicatedCustomer,
                data.transferBody(customerSource, customerTarget, 1.0));
        data.assertBootError(v1Customer, 500, "/accounts/transfer/" + duplicatedCustomer);
        String customerPath = "/v2/accounts/transfer/" + duplicatedCustomer;
        ResponseEntity<String> v2Customer = data.v2Valid(
                HttpMethod.PUT,
                customerPath,
                data.transferBody(customerSource, customerTarget, 1.0));
        data.assertProblem(
                v2Customer, 409, "Duplicate records found for the requested identifier.", customerPath);
        assertRejectedState(new Setup(duplicatedCustomer, customerSource, customerTarget), 100.0, 20.0);

        long sourceCustomer = data.customer();
        long duplicateSource = data.id();
        data.createAccount(sourceCustomer, duplicateSource, 100.0);
        data.createAccount(sourceCustomer, duplicateSource, 150.0);
        long sourceDuplicateTarget = data.account(sourceCustomer, 20.0);
        String sourceV1Path = "/accounts/transfer/" + sourceCustomer;
        ResponseEntity<String> v1Source = data.exchange(
                HttpMethod.PUT,
                sourceV1Path,
                data.transferBody(duplicateSource, sourceDuplicateTarget, 1.0));
        data.assertBootError(v1Source, 500, sourceV1Path);
        String sourceV2Path = "/v2/accounts/transfer/" + sourceCustomer;
        ResponseEntity<String> v2Source = data.v2Valid(
                HttpMethod.PUT,
                sourceV2Path,
                data.transferBody(duplicateSource, sourceDuplicateTarget, 1.0));
        data.assertProblem(
                v2Source, 409, "Duplicate records found for the requested identifier.", sourceV2Path);
        data.assertBalance(sourceDuplicateTarget, 20.0);
        data.assertNoTransactions(sourceDuplicateTarget);

        long targetCustomer = data.customer();
        long targetDuplicateSource = data.account(targetCustomer, 100.0);
        long duplicateTarget = data.id();
        data.createAccount(targetCustomer, duplicateTarget, 20.0);
        data.createAccount(targetCustomer, duplicateTarget, 30.0);
        String targetV1Path = "/accounts/transfer/" + targetCustomer;
        ResponseEntity<String> v1Target = data.exchange(
                HttpMethod.PUT,
                targetV1Path,
                data.transferBody(targetDuplicateSource, duplicateTarget, 1.0));
        data.assertBootError(v1Target, 500, targetV1Path);
        String targetV2Path = "/v2/accounts/transfer/" + targetCustomer;
        ResponseEntity<String> v2Target = data.v2Valid(
                HttpMethod.PUT,
                targetV2Path,
                data.transferBody(targetDuplicateSource, duplicateTarget, 1.0));
        data.assertProblem(
                v2Target, 409, "Duplicate records found for the requested identifier.", targetV2Path);
        data.assertBalance(targetDuplicateSource, 100.0);
        data.assertNoTransactions(targetDuplicateSource);
    }

    @Test
    void malformedBodiesAndPathParametersBecomeProblemDetailsInV2() throws Exception {
        Setup setup = setup(100.0, 20.0);
        String v1Path = "/accounts/transfer/" + setup.customer();
        String v2Path = "/v2/accounts/transfer/" + setup.customer();
        for (String body : new String[] {
                "{",
                "[]",
                "",
                "{\"fromAccountNumber\":\"bad\",\"toAccountNumber\":1,\"transferAmount\":1}"}) {
            ResponseEntity<String> v1 = data.exchange(HttpMethod.PUT, v1Path, body);
            data.assertBootError(v1, 400, v1Path);
            ResponseEntity<String> v2 = data.v2Invalid(HttpMethod.PUT, v2Path, body);
            assertThat(v2.getStatusCode().value()).isEqualTo(400);
            data.assertProblemHasNoBootFields(v2);
            data.assertProblem(v2, 400, null, v2Path);
            assertRejectedState(setup, 100.0, 20.0);
        }

        String v1BadPath = "/accounts/transfer/nope";
        ResponseEntity<String> v1PathResponse = data.exchange(
                HttpMethod.PUT, v1BadPath, data.transferBody(setup.from(), setup.to(), 1.0));
        data.assertBootError(v1PathResponse, 400, v1BadPath);
        String v2BadPath = "/v2/accounts/transfer/nope";
        ResponseEntity<String> v2PathResponse = data.v2Invalid(
                HttpMethod.PUT, v2BadPath, data.transferBody(setup.from(), setup.to(), 1.0));
        assertThat(v2PathResponse.getStatusCode().value()).isEqualTo(400);
        data.assertProblemHasNoBootFields(v2PathResponse);
        data.assertProblem(v2PathResponse, 400, null, v2BadPath);
        assertRejectedState(setup, 100.0, 20.0);
    }

    private Setup setup(double fromBalance, double toBalance) {
        long customer = data.customer();
        return new Setup(customer, data.account(customer, fromBalance), data.account(customer, toBalance));
    }

    private Setup setupOwnedByAnotherCustomer(double fromBalance, double toBalance) {
        long customer = data.customer();
        long owner = data.customer();
        return new Setup(customer, data.account(owner, fromBalance), data.account(owner, toBalance));
    }

    private void assertRejectedState(Setup setup, Double fromBalance, Double toBalance) throws Exception {
        data.assertBalance(setup.from(), fromBalance);
        data.assertBalance(setup.to(), toBalance);
        data.assertNoTransactions(setup.from(), setup.to());
    }

    private record Setup(long customer, long from, long to) {
    }
}
