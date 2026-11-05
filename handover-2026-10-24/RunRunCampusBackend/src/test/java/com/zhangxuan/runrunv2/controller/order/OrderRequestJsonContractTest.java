package com.zhangxuan.runrunv2.controller.order;
import com.zhangxuan.runrunv2.model.order.OrderModels;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderRequestJsonContractTest {
    @Test void clientOwnedAmountAndVersionFieldsAreRejectedDuringDeserialization() {
        ObjectMapper mapper = new ObjectMapper();
        assertThrows(Exception.class, () -> mapper.readValue(
                "{\"serviceType\":\"PACKAGE_SMALL\",\"zoneCode\":\"MEIYUAN\",\"servicePointCode\":\"TEST_POINT\",\"destination\":\"x\",\"configVersion\":0,\"originalFeeCents\":1}",
                OrderModels.CreateRequest.class));
    }
}
