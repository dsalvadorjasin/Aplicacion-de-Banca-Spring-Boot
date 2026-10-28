package com.coding.exercise.bankapp.v2.moneymovement;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

public record TransferRequest(
        @JsonDeserialize(using = StrictLongDeserializer.class)
        Long fromAccountNumber,
        @JsonDeserialize(using = StrictLongDeserializer.class)
        Long toAccountNumber,
        @JsonDeserialize(using = StrictDoubleDeserializer.class)
        Double transferAmount) {
}
