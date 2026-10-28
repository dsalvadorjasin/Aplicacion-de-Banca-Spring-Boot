package com.coding.exercise.bankapp.v2.moneymovement;

import java.util.List;

import com.coding.exercise.bankapp.domain.TransactionDetails;
import com.coding.exercise.bankapp.v2.ApiV2;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiV2.BASE_PATH + "/accounts")
public class MoneyMovementV2Controller {
    private final MoneyMovementV2Service service;

    public MoneyMovementV2Controller(MoneyMovementV2Service service) {
        this.service = service;
    }

    @PutMapping("/transfer/{customerNumber}")
    public TransferResult transfer(
            @PathVariable Long customerNumber,
            @RequestBody(required = false) TransferRequest request) {
        return service.transfer(customerNumber, request);
    }

    @GetMapping("/transactions/{accountNumber}")
    public List<TransactionDetails> transactions(@PathVariable Long accountNumber) {
        return service.transactions(accountNumber);
    }
}
