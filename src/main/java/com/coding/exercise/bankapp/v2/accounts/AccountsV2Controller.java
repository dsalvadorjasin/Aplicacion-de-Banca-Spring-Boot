package com.coding.exercise.bankapp.v2.accounts;

import com.coding.exercise.bankapp.domain.AccountInformation;
import com.coding.exercise.bankapp.v2.ApiV2;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiV2.BASE_PATH + "/accounts")
@Tag(name = "Accounts V2")
public class AccountsV2Controller {
    private final AccountsV2Service accountsV2Service;

    public AccountsV2Controller(AccountsV2Service accountsV2Service) {
        this.accountsV2Service = accountsV2Service;
    }

    @GetMapping("/{accountNumber}")
    public AccountInformation getAccount(@PathVariable Long accountNumber) {
        return accountsV2Service.findAccount(accountNumber);
    }

    @PostMapping("/add/{customerNumber}")
    public ResponseEntity<AccountInformation> addAccount(
            @PathVariable Long customerNumber, @RequestBody AccountInformation accountInformation) {
        AccountInformation created = accountsV2Service.createAccount(customerNumber, accountInformation);
        return ResponseEntity.created(
                ApiV2.location("/v2/accounts/{accountNumber}", created.getAccountNumber()))
                .body(created);
    }
}
