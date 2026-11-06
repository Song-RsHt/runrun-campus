package com.zhangxuan.runrunv2.controller.order;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhangxuan.runrunv2.dto.request.order.CreateRequest;
import org.junit.jupiter.api.Test;

class OrderRequestJsonContractTest {

  @Test
  void clientOwnedAmountAndVersionFieldsAreRejectedDuringDeserialization() {
    ObjectMapper mapper = new ObjectMapper();
    assertThrows(
        Exception.class,
        () ->
            mapper.readValue(
                "{\"serviceType\":\"PACKAGE_SMALL\",\"zoneCode\":\"MEIYUAN\",\"servicePointCode\":\"TEST_POINT\",\"destination\":\"x\",\"configVersion\":0,\"originalFeeCents\":1}",
                CreateRequest.class));
  }
}
