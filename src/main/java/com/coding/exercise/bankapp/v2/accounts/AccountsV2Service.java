package com.coding.exercise.bankapp.v2.accounts;

import java.util.Optional;

import com.coding.exercise.bankapp.domain.AccountInformation;
import com.coding.exercise.bankapp.domain.BankInformation;
import com.coding.exercise.bankapp.model.Account;
import com.coding.exercise.bankapp.model.Customer;
import com.coding.exercise.bankapp.repository.AccountRepository;
import com.coding.exercise.bankapp.repository.CustomerRepository;
import com.coding.exercise.bankapp.service.BankingServiceImpl;
import com.coding.exercise.bankapp.service.helper.BankingServiceHelper;
import com.coding.exercise.bankapp.v2.ApiV2BadRequestException;
import com.coding.exercise.bankapp.v2.ApiV2ConflictException;
import com.coding.exercise.bankapp.v2.ApiV2NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class AccountsV2Service {
    private final BankingServiceImpl bankingService;
    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final BankingServiceHelper bankingServiceHelper;
    private final TransactionTemplate transactionTemplate;
    private final Object lock = new Object();

    public AccountsV2Service(
            BankingServiceImpl bankingService,
            CustomerRepository customerRepository,
            AccountRepository accountRepository,
            BankingServiceHelper bankingServiceHelper,
            PlatformTransactionManager transactionManager) {
        this.bankingService = bankingService;
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.bankingServiceHelper = bankingServiceHelper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public AccountInformation findAccount(Long accountNumber) {
        ResponseEntity<Object> response = bankingService.findByAccountNumber(accountNumber);
        if (response.getStatusCode() == HttpStatus.FOUND
                && response.getBody() instanceof AccountInformation accountInformation) {
            Double balance = accountInformation.getAccountBalance();
            if (balance != null && !Double.isFinite(balance)) {
                throw new ApiV2ConflictException(
                        "Account Number " + accountNumber + " has a stored balance that is not a finite number.");
            }
            return accountInformation;
        }
        throw new ApiV2NotFoundException("Account Number " + accountNumber + " not found.");
    }

    public AccountInformation createAccount(Long customerNumber, AccountInformation accountInformation) {
        validate(accountInformation);
        synchronized (lock) {
            return transactionTemplate.execute(transactionStatus -> {
                Optional<Customer> customer = customerRepository.findByCustomerNumber(customerNumber);
                if (customer.isEmpty()) {
                    throw new ApiV2NotFoundException("Customer Number " + customerNumber + " not found.");
                }

                Long accountNumber = accountInformation.getAccountNumber();
                if (accountRepository.findByAccountNumber(accountNumber).isPresent()) {
                    throw new ApiV2ConflictException("Account Number " + accountNumber + " already exists.");
                }

                bankingService.addNewAccount(accountInformation, customerNumber);
                Optional<Account> created = accountRepository.findByAccountNumber(accountNumber);
                return created.map(bankingServiceHelper::convertToAccountDomain)
                        .orElseThrow(() -> new ApiV2ConflictException(
                                "Account Number " + accountNumber + " could not be read after creation."));
            });
        }
    }

    private void validate(AccountInformation accountInformation) {
        if (accountInformation == null || accountInformation.getAccountNumber() == null) {
            throw new ApiV2BadRequestException("accountNumber is required.");
        }
        BankInformation bankInformation = accountInformation.getBankInformation();
        if (bankInformation == null) {
            throw new ApiV2BadRequestException("bankInformation is required.");
        }
        if (bankInformation.getBranchAddress() == null) {
            throw new ApiV2BadRequestException("bankInformation.branchAddress is required.");
        }
        Double balance = accountInformation.getAccountBalance();
        if (balance != null && !Double.isFinite(balance)) {
            throw new ApiV2BadRequestException("accountBalance must be a finite number.");
        }
    }
}
