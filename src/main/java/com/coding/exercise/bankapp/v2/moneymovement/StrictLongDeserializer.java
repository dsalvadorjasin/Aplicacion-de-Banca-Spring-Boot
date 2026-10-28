package com.coding.exercise.bankapp.v2.moneymovement;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

public final class StrictLongDeserializer extends StdDeserializer<Long> {
    private static final long serialVersionUID = 1L;

    public StrictLongDeserializer() {
        super(Long.class);
    }

    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT) {
            return parser.getLongValue();
        }
        return (Long) context.handleUnexpectedToken(Long.class, parser);
    }
}
