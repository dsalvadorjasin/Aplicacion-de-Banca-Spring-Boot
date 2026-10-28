package com.coding.exercise.bankapp.v2.moneymovement;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import com.coding.exercise.bankapp.domain.TransactionDetails;
import com.coding.exercise.bankapp.domain.TransferDetails;
import com.coding.exercise.bankapp.model.Account;
import com.coding.exercise.bankapp.repository.AccountRepository;
import com.coding.exercise.bankapp.repository.CustomerRepository;
import com.coding.exercise.bankapp.service.BankingServiceImpl;
import com.coding.exercise.bankapp.v2.ApiV2BadRequestException;
import com.coding.exercise.bankapp.v2.ApiV2BusinessRuleException;
import com.coding.exercise.bankapp.v2.ApiV2NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MoneyMovementV2Service {
    private final BankingServiceImpl bankingService;
    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;

    public MoneyMovementV2Service(
            BankingServiceImpl bankingService,
            CustomerRepository customerRepository,
            AccountRepository accountRepository) {
        this.bankingService = bankingService;
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional(rollbackFor = Exception.class)
    public TransferResult transfer(Long customerNumber, TransferRequest request) {
        if (request == null) {
            throw new ApiV2BadRequestException("Transfer request is required.");
        }
        StringJoiner missingFields = new StringJoiner(", ");
        if (request.fromAccountNumber() == null) {
            missingFields.add("fromAccountNumber");
        }
        if (request.toAccountNumber() == null) {
            missingFields.add("toAccountNumber");
        }
        if (request.transferAmount() == null) {
            missingFields.add("transferAmount");
        }
        if (missingFields.length() > 0) {
            throw new ApiV2BadRequestException(missingFields + " must not be null.");
        }

        double amount = request.transferAmount();
        if (!Double.isFinite(amount)) {
            throw new ApiV2BadRequestException("transferAmount must be a finite number.");
        }
        if (amount <= 0) {
            throw new ApiV2BadRequestException("transferAmount must be greater than 0.");
        }

        if (customerRepository.findByCustomerNumber(customerNumber).isEmpty()) {
            throw new ApiV2NotFoundException("Customer Number " + customerNumber + " not found.");
        }

        Long fromNumber = request.fromAccountNumber();
        Long toNumber = request.toAccountNumber();
        Account from = accountRepository.findByAccountNumber(fromNumber)
                .orElseThrow(() -> new ApiV2NotFoundException(
                        "From Account Number " + fromNumber + " not found."));
        Account to = accountRepository.findByAccountNumber(toNumber)
                .orElseThrow(() -> new ApiV2NotFoundException(
                        "To Account Number " + toNumber + " not found."));

        Double fromBalance = from.getAccountBalance();
        Double toBalance = to.getAccountBalance();
        if (fromBalance == null || !Double.isFinite(fromBalance)) {
            throw new ApiV2BusinessRuleException("Account Number " + fromNumber + " has no valid balance.");
        }
        if (toBalance == null || !Double.isFinite(toBalance)) {
            throw new ApiV2BusinessRuleException("Account Number " + toNumber + " has no valid balance.");
        }
        if (fromBalance < amount) {
            throw new ApiV2BusinessRuleException("Insufficient Funds.");
        }

        double resultingFromBalance;
        double resultingToBalance;
        if (fromNumber.equals(toNumber)) {
            resultingFromBalance = (fromBalance - amount) + amount;
            resultingToBalance = resultingFromBalance;
        } else {
            resultingFromBalance = fromBalance - amount;
            resultingToBalance = toBalance + amount;
        }
        if (!Double.isFinite(resultingFromBalance) || !Double.isFinite(resultingToBalance)) {
            throw new ApiV2BusinessRuleException("Transfer would make an account balance non-finite.");
        }

        ResponseEntity<Object> transferResponse = bankingService.transferDetails(
                new TransferDetails(fromNumber, toNumber, amount), customerNumber);
        if (transferResponse.getStatusCode() != HttpStatus.OK) {
            String detail = String.valueOf(transferResponse.getBody());
            if (transferResponse.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ApiV2NotFoundException(detail);
            }
            if (transferResponse.getStatusCode() == HttpStatus.BAD_REQUEST) {
                throw new ApiV2BusinessRuleException(detail);
            }
            throw new IllegalStateException("Unexpected transfer response status: "
                    + transferResponse.getStatusCode().value());
        }

        Double updatedFromBalance = from.getAccountBalance();
        Double updatedToBalance = to.getAccountBalance();
        if (updatedFromBalance == null || !Double.isFinite(updatedFromBalance)
                || updatedToBalance == null || !Double.isFinite(updatedToBalance)) {
            throw new ApiV2BusinessRuleException("Transfer would make an account balance non-finite.");
        }
        return new TransferResult(
                customerNumber, fromNumber, toNumber, amount, updatedFromBalance, updatedToBalance);
    }

    @Transactional(readOnly = true)
    public List<TransactionDetails> transactions(Long accountNumber) {
        if (accountRepository.findByAccountNumber(accountNumber).isEmpty()) {
            throw new ApiV2NotFoundException("Account Number " + accountNumber + " not found.");
        }
        return new ArrayList<>(bankingService.findTransactionsByAccountNumber(accountNumber));
    }
}
