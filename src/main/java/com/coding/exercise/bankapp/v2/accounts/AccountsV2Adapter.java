package com.coding.exercise.bankapp.v2.accounts;

import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.coding.exercise.bankapp.domain.AccountInformation;
import com.coding.exercise.bankapp.repository.AccountRepository;
import com.coding.exercise.bankapp.repository.CustomerRepository;
import com.coding.exercise.bankapp.service.BankingServiceImpl;
import com.coding.exercise.bankapp.v2.BadRequestException;
import com.coding.exercise.bankapp.v2.ConflictException;
import com.coding.exercise.bankapp.v2.NotFoundException;

@Service
public class AccountsV2Adapter {

	private final BankingServiceImpl bankingService;
	private final AccountRepository accountRepository;
	private final CustomerRepository customerRepository;
	private final TransactionTemplate transactions;

	public AccountsV2Adapter(BankingServiceImpl bankingService, AccountRepository accountRepository,
			CustomerRepository customerRepository, PlatformTransactionManager transactionManager) {
		this.bankingService = bankingService;
		this.accountRepository = accountRepository;
		this.customerRepository = customerRepository;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	public AccountInformation find(long accountNumber) {
		requirePositive(accountNumber, "Account number");
		ResponseEntity<Object> response = bankingService.findByAccountNumber(accountNumber);
		if (response.getStatusCode().value() == 404) {
			throw new NotFoundException("Account " + accountNumber + " not found");
		}
		return (AccountInformation) response.getBody();
	}

	public synchronized AccountInformation create(AccountInformation account, long customerNumber) {
		requirePositive(customerNumber, "Customer number");
		validate(account);
		// Commit before releasing the monitor, not after returning through a transactional proxy.
		return transactions.execute(status -> createAccount(account, customerNumber));
	}

	private AccountInformation createAccount(AccountInformation account, long customerNumber) {
		if (customerRepository.findByCustomerNumber(customerNumber).isEmpty()) {
			throw new NotFoundException("Customer " + customerNumber + " not found");
		}
		boolean exists;
		try {
			exists = accountRepository.findByAccountNumber(account.getAccountNumber()).isPresent();
		} catch (IncorrectResultSizeDataAccessException exception) {
			exists = true;
		}
		if (exists) {
			throw new ConflictException("Account " + account.getAccountNumber() + " already exists");
		}
		try {
			bankingService.addNewAccount(account, customerNumber);
			return find(account.getAccountNumber());
		} catch (IncorrectResultSizeDataAccessException exception) {
			throw new ConflictException("Account " + account.getAccountNumber() + " already exists");
		}
	}

	private static void validate(AccountInformation account) {
		if (account == null || account.getAccountNumber() == null) {
			throw new BadRequestException("Account number is required");
		}
		requirePositive(account.getAccountNumber(), "Account number");
		Double balance = account.getAccountBalance();
		if (balance == null || !Double.isFinite(balance) || balance < 0) {
			throw new BadRequestException("Account balance must be a finite, non-negative number");
		}
		if (account.getBankInformation() == null || account.getBankInformation().getBranchAddress() == null) {
			throw new BadRequestException("Bank information and branch address are required");
		}
	}

	private static void requirePositive(long number, String name) {
		if (number <= 0) {
			throw new BadRequestException(name + " must be positive");
		}
	}
}
