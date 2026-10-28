package com.coding.exercise.bankapp;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unmodified Hibernate 5/H2 UUID binary(255) padding defects. These expectations
 * are expected to change when the persistence stack changes. The intended HTTP
 * contract is exercised separately by BankingHttpContractTest using binary(16).
 */
@Tag("baseline-defect")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:baseline-defect;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"})
class BaselinePersistenceDefectTest extends HttpTestSupport {
    @Autowired JdbcTemplate jdbc;

    @Test void storedCustomerReadsUpdatesDeletesAndDependentAccountCreationFail() throws Exception {
        long c = id(); createCustomer(c);
        assertThat(jdbc.queryForObject("select octet_length(cust_id) from customer where customer_number=?", Integer.class, c)).isEqualTo(255);
        error(get("/customers/" + c), 500, "/customers/" + c);
        error(get("/customers/all"), 500, "/customers/all");
        error(request(HttpMethod.PUT, "/customers/" + c, customer(c)), 500, "/customers/" + c);
        error(request(HttpMethod.DELETE, "/customers/" + c, null), 500, "/customers/" + c);
        error(request(HttpMethod.POST, "/accounts/add/" + c, account(id(), 100)), 500, "/accounts/add/" + c);
        error(transfer(c, id(), id(), 1), 500, "/accounts/transfer/" + c);
        // An absent identifier disproves a blanket routing/security failure.
        text(get("/customers/" + id()), 200, null);
    }

    @Test void storedAccountWithNestedBankAndTransactionLookupFail() throws Exception {
        long a = id();
        String address = java.util.UUID.randomUUID().toString();
        String bank = java.util.UUID.randomUUID().toString();
        jdbc.update("insert into address (addr_id, city) values (cast(? as uuid), 'Boston')", address);
        jdbc.update("insert into bank_info (bank_id, branch_address_addr_id) values (cast(? as uuid), cast(? as uuid))", bank, address);
        jdbc.update("insert into account (acct_id, account_number, account_balance, bank_information_bank_id) values (random_uuid(), ?, 10, cast(? as uuid))", a, bank);
        assertThat(jdbc.queryForObject("select octet_length(acct_id) from account where account_number=?", Integer.class, a)).isEqualTo(255);
        error(get("/accounts/" + a), 500, "/accounts/" + a);
        error(get("/accounts/transactions/" + a), 500, "/accounts/transactions/" + a);
        text(get("/accounts/transactions/" + id()), 200, "[]");
    }
}
