package com.zhangxuan.runrunv2.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class OrderJson {

  private final ObjectMapper mapper;

  public OrderJson(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public String write(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot serialize validated order data");
    }
  }

  public <T> T read(String value, Class<T> type) {
    try {
      return mapper.readValue(value, type);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Invalid stored order data");
    }
  }
}
