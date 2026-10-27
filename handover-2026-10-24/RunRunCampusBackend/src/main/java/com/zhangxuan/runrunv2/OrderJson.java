package com.zhangxuan.runrunv2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
class OrderJson {
    private final ObjectMapper mapper;
    OrderJson(ObjectMapper mapper) { this.mapper = mapper; }
    String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot serialize validated order data"); }
    }
    <T> T read(String value, Class<T> type) {
        try { return mapper.readValue(value, type); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Invalid stored order data"); }
    }
}
