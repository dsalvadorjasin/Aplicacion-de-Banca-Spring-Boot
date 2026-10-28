package com.coding.exercise.bankapp.v2.moneymovement;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.coding.exercise.bankapp.domain.TransactionDetails;
import com.coding.exercise.bankapp.domain.TransferDetails;
import com.coding.exercise.bankapp.repository.AccountRepository;
import com.coding.exercise.bankapp.service.BankingServiceImpl;
import com.coding.exercise.bankapp.v2.BadRequestException;
import com.coding.exercise.bankapp.v2.BusinessRuleException;
import com.coding.exercise.bankapp.v2.NotFoundException;

@Component
public class MoneyMovementAdapter {

	private final BankingServiceImpl bankingService;
	private final AccountRepository accounts;

	public MoneyMovementAdapter(BankingServiceImpl bankingService, AccountRepository accounts) {
		this.bankingService = bankingService;
		this.accounts = accounts;
	}

	public CompletedTransfer transfer(Long customerNumber, TransferDetails transfer) {
		if (transfer == null || transfer.getFromAccountNumber() == null || transfer.getToAccountNumber() == null
				|| transfer.getTransferAmount() == null || !Double.isFinite(transfer.getTransferAmount())
				|| transfer.getTransferAmount() <= 0) {
			throw new BadRequestException("Account numbers and a positive transferAmount are required.");
		}
		ResponseEntity<Object> result = bankingService.transferDetails(transfer, customerNumber);
		return switch (result.getStatusCode().value()) {
			case 200 -> new CompletedTransfer(customerNumber, transfer.getFromAccountNumber(),
					transfer.getToAccountNumber(), transfer.getTransferAmount());
			case 404 -> throw new NotFoundException(String.valueOf(result.getBody()));
			case 400 -> throw new BusinessRuleException(String.valueOf(result.getBody()));
			default -> throw new IllegalStateException("Unexpected transfer status");
		};
	}

	public List<TransactionDetails> transactions(Long accountNumber) {
		if (accounts.findByAccountNumber(accountNumber).isEmpty()) {
			throw new NotFoundException("Account Number " + accountNumber + " not found.");
		}
		return bankingService.findTransactionsByAccountNumber(accountNumber);
	}

	public record CompletedTransfer(Long customerNumber, Long fromAccountNumber, Long toAccountNumber,
			Double transferAmount) {
	}
}
