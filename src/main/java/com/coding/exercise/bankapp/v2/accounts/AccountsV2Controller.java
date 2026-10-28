package com.coding.exercise.bankapp.v2.accounts;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.coding.exercise.bankapp.domain.AccountInformation;
import com.coding.exercise.bankapp.v2.ApiV2;

@RestController
@RequestMapping(ApiV2.BASE_PATH + "/accounts")
public class AccountsV2Controller {

	private final AccountsV2Adapter accounts;

	public AccountsV2Controller(AccountsV2Adapter accounts) {
		this.accounts = accounts;
	}

	@GetMapping("/{accountNumber}")
	public AccountInformation get(@PathVariable long accountNumber) {
		return accounts.find(accountNumber);
	}

	@PostMapping(path = "/add/{customerNumber}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<AccountInformation> create(@RequestBody AccountInformation account,
			@PathVariable long customerNumber, HttpServletRequest request) {
		AccountInformation created = accounts.create(account, customerNumber);
		URI location = URI.create(request.getContextPath() + ApiV2.BASE_PATH + "/accounts/"
				+ created.getAccountNumber());
		return ResponseEntity.created(location).body(created);
	}
}
