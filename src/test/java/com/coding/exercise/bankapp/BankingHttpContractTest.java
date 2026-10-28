package com.coding.exercise.bankapp;

import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.dialect.H2Dialect;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import java.sql.Types;
import static org.assertj.core.api.Assertions.assertThat;

/** Real HTTP contract with only UUID column width repaired in an isolated test database. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:http-contract;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.database-platform=com.coding.exercise.bankapp.BankingHttpContractTest$UuidDialect"})
class BankingHttpContractTest extends HttpTestSupport {
    /** Hibernate 5 defaults UUID columns to binary(255); UUIDs require exactly 16 bytes. */
    public static class UuidDialect extends H2Dialect {
        @Override public String getTypeName(int code, long length, int precision, int scale) {
            return code == Types.BINARY ? "binary(16)" : super.getTypeName(code, length, precision, scale);
        }
    }

    @Test void customerCrud() throws Exception {
        long c = id(); createCustomer(c);
        var found = get("/customers/" + c);
        assertThat(found.getStatusCodeValue()).isEqualTo(200);
        assertThat(json(found)).isEqualTo(mapper.readTree(customer(c)));
        var all = get("/customers/all");
        assertThat(all.getStatusCodeValue()).isEqualTo(200);
        assertThat(json(all)).anySatisfy(node -> assertThat(node).isEqualTo(mapper.readTree(customer(c))));
        String updated = customer(c).replace("Ada", "Grace").replace("Boston", "Cambridge").replace("123", "789");
        text(request(HttpMethod.PUT, "/customers/" + c, updated), 200, "Success: Customer updated.");
        var afterUpdate = get("/customers/" + c);
        assertThat(afterUpdate.getStatusCodeValue()).isEqualTo(200);
        assertThat(json(afterUpdate)).isEqualTo(mapper.readTree(updated));
        text(request(HttpMethod.DELETE, "/customers/" + c, null), 200, "Success: Customer deleted.");
        text(get("/customers/" + c), 200, null);
        text(request(HttpMethod.DELETE, "/customers/" + c, null), 400, "Customer does not exist.");
    }

    @Test void missingEntitiesAndFalsePositiveAccountCreation() throws Exception {
        long c = id(), a = id();
        text(get("/customers/" + c), 200, null);
        text(request(HttpMethod.PUT, "/customers/" + c, customer(c)), 404, "Customer Number " + c + " not found.");
        text(request(HttpMethod.DELETE, "/customers/" + c, null), 400, "Customer does not exist.");
        createAccount(c, a, 100);
        text(get("/accounts/" + a), 404, "Account Number " + a + " not found.");
        text(get("/accounts/transactions/" + a), 200, "[]");
        text(transfer(c, a, id(), 10), 404, "Customer Number " + c + " not found.");
    }

    @Test void initialBalancesTransferAndTransactionHistory() throws Exception {
        long c = id(), a = id(), b = id(); createCustomer(c);
        createAccount(c, a, 100); createAccount(c, b, 20);
        assertAccount(a, 100); assertAccount(b, 20);
        text(get("/accounts/transactions/" + a), 200, "[]");
        text(transfer(c, a, b, 25), 200, "Success: Amount transferred for Customer Number " + c);
        assertAccount(a, 75); assertAccount(b, 45);
        assertTransaction(a, "DEBIT", 25); assertTransaction(b, "CREDIT", 25);
        text(transfer(c, a, b, 76), 400, "Insufficient Funds.");
        assertAccount(a, 75); assertAccount(b, 45);
        assertTransaction(a, "DEBIT", 25);
        long missing = id();
        text(transfer(c, missing, b, 1), 404, "From Account Number " + missing + " not found.");
        text(transfer(c, a, missing, 1), 404, "To Account Number " + missing + " not found.");
    }

    @Test void transferDoesNotEnforceOwnershipOrPositiveAmounts() throws Exception {
        long c = id(), other = id(), a = id(), b = id(); createCustomer(c); createCustomer(other);
        createAccount(other, a, 100); createAccount(other, b, 20);
        text(transfer(c, a, b, -5), 200, "Success: Amount transferred for Customer Number " + c);
        assertAccount(a, 105); assertAccount(b, 15);
        text(transfer(c, a, b, 0), 200, "Success: Amount transferred for Customer Number " + c);
        assertThat(json(get("/accounts/transactions/" + a))).hasSize(2);
    }

    @Test void customerDeletionLeavesAccountsAccessible() throws Exception {
        long c = id(), a = id(); createCustomer(c); createAccount(c, a, 12);
        text(request(HttpMethod.DELETE, "/customers/" + c, null), 200, "Success: Customer deleted.");
        assertAccount(a, 12);
    }

    @Test void duplicateCustomerAndAccountNumbersAreAcceptedButAmbiguousReadsFail() throws Exception {
        long c = id(), a = id(); createCustomer(c); createCustomer(c);
        error(get("/customers/" + c), 500, "/customers/" + c);
        long owner = id(); createCustomer(owner); createAccount(owner, a, 1); createAccount(owner, a, 2);
        error(get("/accounts/" + a), 500, "/accounts/" + a);
    }

    @Test void malformedWrongTypeAndMissingBodies() throws Exception {
        String[] paths = {"/customers/add", "/customers/" + id(), "/accounts/add/" + id(), "/accounts/transfer/" + id()};
        HttpMethod[] methods = {HttpMethod.POST, HttpMethod.PUT, HttpMethod.POST, HttpMethod.PUT};
        String[] invalid = {"{", "", "[]", "{\"customerNumber\":\"not-a-number\",\"accountNumber\":\"bad\",\"transferAmount\":\"bad\"}"};
        for (int i = 0; i < paths.length; i++) for (String body : invalid) error(request(methods[i], paths[i], body), 400, paths[i]);
        for (String path : new String[]{"/customers/nope", "/accounts/nope", "/accounts/transactions/nope"}) error(get(path), 400, path);
        error(request(HttpMethod.POST, "/customers/add", "{}"), 500, "/customers/add");
        long c = id(); createCustomer(c);
        error(request(HttpMethod.PUT, "/customers/" + c, "{}"), 500, "/customers/" + c);
        error(request(HttpMethod.POST, "/accounts/add/" + c, "{}"), 500, "/accounts/add/" + c);
        long a = id(), b = id(); createAccount(c, a, 1); createAccount(c, b, 2);
        error(request(HttpMethod.PUT, "/accounts/transfer/" + c, "{\"fromAccountNumber\":" + a + ",\"toAccountNumber\":" + b + "}"), 500, "/accounts/transfer/" + c);
    }

    void assertAccount(long a, double balance) throws Exception {
        var response = get("/accounts/" + a);
        assertThat(response.getStatusCodeValue()).isEqualTo(302);
        JsonNode expected = mapper.readTree(account(a, balance));
        ((com.fasterxml.jackson.databind.node.ObjectNode) expected).putNull("accountCreated");
        assertThat(json(response)).isEqualTo(expected);
        assertThat(response.getHeaders().getLocation()).isNull();
    }
    void assertTransaction(long a, String type, double amount) throws Exception {
        var response = get("/accounts/transactions/" + a);
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        JsonNode list = json(response); assertThat(list).hasSize(1);
        JsonNode tx = list.get(0);
        assertThat(tx.size()).isEqualTo(4);
        assertThat(tx.get("accountNumber").asLong()).isEqualTo(a);
        assertThat(tx.get("txType").asText()).isEqualTo(type);
        assertThat(tx.get("txAmount").asDouble()).isEqualTo(amount);
        assertThat(tx.hasNonNull("txDateTime")).isTrue();
    }
}
