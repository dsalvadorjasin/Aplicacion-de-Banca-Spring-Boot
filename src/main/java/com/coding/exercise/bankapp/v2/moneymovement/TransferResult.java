package com.coding.exercise.bankapp.v2.moneymovement;

public record TransferResult(
        Long customerNumber,
        Long fromAccountNumber,
        Long toAccountNumber,
        Double transferAmount,
        Double fromAccountBalance,
        Double toAccountBalance) {
}
