package com.coding.exercise.bankapp.v2.moneymovement;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.coding.exercise.bankapp.domain.TransactionDetails;
import com.coding.exercise.bankapp.domain.TransferDetails;
import com.coding.exercise.bankapp.v2.ApiV2;
import com.coding.exercise.bankapp.v2.moneymovement.MoneyMovementAdapter.CompletedTransfer;

@RestController
@RequestMapping(ApiV2.BASE_PATH + "/accounts")
public class MoneyMovementController {

	private final MoneyMovementAdapter adapter;

	public MoneyMovementController(MoneyMovementAdapter adapter) {
		this.adapter = adapter;
	}

	@PutMapping("/transfer/{customerNumber}")
	public CompletedTransfer transfer(@PathVariable Long customerNumber, @RequestBody TransferDetails transfer) {
		return adapter.transfer(customerNumber, transfer);
	}

	@GetMapping("/transactions/{accountNumber}")
	public List<TransactionDetails> transactions(@PathVariable Long accountNumber) {
		return adapter.transactions(accountNumber);
	}
}
