package com.coding.exercise.bankapp.v2.moneymovement;

public record TransferRequest(
        Long fromAccountNumber,
        Long toAccountNumber,
        Double transferAmount) {
}
