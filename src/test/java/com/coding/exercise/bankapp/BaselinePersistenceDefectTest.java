package com.coding.exercise.bankapp;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Baseline Hibernate 5/H2 UUID binary(255) padding defect on the unmodified schema.
 * Hibernate 6 (Spring Boot 3) maps UUID ids to the native H2 uuid type (16 bytes),
 * so stored entities now read, update and delete normally; see MIGRATION_NOTES.md.
 */
@Tag("baseline-defect")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:baseline-defect;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"})
class BaselinePersistenceDefectTest extends HttpTestSupport {
    @Autowired JdbcTemplate jdbc;

    @Test void storedCustomerReadsUpdatesDeletesAndDependentAccountCreationSucceed() throws Exception {
        long c = id(); createCustomer(c);
        assertThat(jdbc.queryForObject("select octet_length(cust_id) from customer where customer_number=?", Integer.class, c)).isEqualTo(16);
        var found = get("/customers/" + c);
        assertThat(found.getStatusCodeValue()).isEqualTo(200);
        assertThat(json(found)).isEqualTo(mapper.readTree(customer(c)));
        var all = get("/customers/all");
        assertThat(all.getStatusCodeValue()).isEqualTo(200);
        assertThat(json(all)).anySatisfy(node -> assertThat(node).isEqualTo(mapper.readTree(customer(c))));
        text(request(HttpMethod.PUT, "/customers/" + c, customer(c)), 200, "Success: Customer updated.");
        long a = id(), b = id(); createAccount(c, a, 100); createAccount(c, b, 0);
        text(transfer(c, a, b, 1), 200, "Success: Amount transferred for Customer Number " + c);
        text(request(HttpMethod.DELETE, "/customers/" + c, null), 200, "Success: Customer deleted.");
        // An absent identifier disproves a blanket routing/security failure.
        text(get("/customers/" + id()), 200, null);
    }

    @Test void storedAccountWithNestedBankAndTransactionLookupSucceed() throws Exception {
        long a = id();
        String address = java.util.UUID.randomUUID().toString();
        String bank = java.util.UUID.randomUUID().toString();
        jdbc.update("insert into address (addr_id, city) values (cast(? as uuid), 'Boston')", address);
        jdbc.update("insert into bank_info (bank_id, branch_address_addr_id) values (cast(? as uuid), cast(? as uuid))", bank, address);
        jdbc.update("insert into account (acct_id, account_number, account_balance, bank_information_bank_id) values (random_uuid(), ?, 10, cast(? as uuid))", a, bank);
        assertThat(jdbc.queryForObject("select octet_length(acct_id) from account where account_number=?", Integer.class, a)).isEqualTo(16);
        var account = get("/accounts/" + a);
        assertThat(account.getStatusCodeValue()).isEqualTo(302);
        assertThat(json(account).get("accountNumber").asLong()).isEqualTo(a);
        assertThat(json(account).get("accountBalance").asDouble()).isEqualTo(10);
        assertThat(json(account).get("bankInformation").get("branchAddress").get("city").asText()).isEqualTo("Boston");
        text(get("/accounts/transactions/" + a), 200, "[]");
        text(get("/accounts/transactions/" + id()), 200, "[]");
    }
}
