package com.zhangxuan.runruncampus.v2.foundation;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;
import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonDeserialize(using = IntegerCents.Deserializer.class)
public @interface IntegerCents {
    class Deserializer extends JsonDeserializer<Long> {
        @Override public Long deserialize(JsonParser p, DeserializationContext context) throws IOException {
            // Jackson's normal long coercion accepts decimal/string values; money must not be truncated.
            if (p.currentToken() != JsonToken.VALUE_NUMBER_INT)
                return (Long) context.handleUnexpectedToken(Long.class, p);
            return Cents.nonNegative(p.getLongValue());
        }
        @Override public Long getNullValue(DeserializationContext context) throws JsonMappingException {
            throw JsonMappingException.from(context.getParser(), "Integer cents required");
        }
    }
}
