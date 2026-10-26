package com.zhangxuan.runruncampus.v2.foundation;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import com.zhangxuan.runruncampus.api.ApiExceptionHandler;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FoundationTest {
    final ObjectMapper json=Jackson2ObjectMapperBuilder.json().build();
    record Dto(@ApiId Long id, @IntegerCents Long amountCents, long version, @ApiTime Instant createdAt) {}

    @Test void onlyIdsBecomeStringsWhileCentsAndVersionStayNumbersAndTimeHasOffset() throws Exception {
        JsonNode value=json.readTree(json.writeValueAsString(new Dto(Long.MAX_VALUE,200L,3,Instant.parse("2026-10-26T01:00:00Z"))));
        assertEquals("9223372036854775807",value.get("id").textValue());
        assertTrue(value.get("amountCents").isIntegralNumber());assertEquals(3,value.get("version").intValue());
        assertEquals("2026-10-26T01:00:00Z",value.get("createdAt").textValue());
        assertNull(json.readTree(json.writeValueAsString(new Dto(null,0L,0,null))).get("id").textValue());
    }
    @Test void moneyRejectsDecimalsStringsNegativesAndOverflow() throws Exception {
        for(String amount:new String[]{"1.5","\"200\"","-1","9223372036854775808","null"})
            assertThrows(Exception.class,()->json.readValue("{\"amountCents\":"+amount+"}",Dto.class),amount);
        assertEquals(200L,json.readValue("{\"amountCents\":200}",Dto.class).amountCents());
        assertThrows(V2Exception.class,()->Cents.add(Long.MAX_VALUE,1));
        for(long value:new long[]{0,1,2,3,99,100,101,Long.MAX_VALUE}) {
            var split=Cents.split(value);assertEquals(value,split.platformCents()+split.courierCents());
            assertEquals(java.math.BigInteger.valueOf(value).multiply(java.math.BigInteger.valueOf(30)).divide(java.math.BigInteger.valueOf(100)).longValueExact(),split.platformCents());
        }
    }
    @Test void authorizationHidesBothVersionAndFlowAndVersionIsIndependentOfFlow() {
        assertEquals(V2Error.RESOURCE_NOT_FOUND,assertThrows(V2Exception.class,()->ResourceChecks.order(false,1,5,0L)).error());
        assertEquals(V2Error.ORDER_FLOW_VERSION_MISMATCH,assertThrows(V2Exception.class,()->ResourceChecks.order(true,1,5,0L)).error());
        assertEquals(V2Error.ORDER_STATE_CONFLICT,assertThrows(V2Exception.class,()->ResourceChecks.order(true,2,5,0L)).error());
        assertDoesNotThrow(()->ResourceChecks.order(true,2,5,5L));
        assertEquals(37,V2Error.values().length);
    }
    @Test void v2ErrorsAndParsingAreStableAndLegacyMessageOnlyIsPreserved() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new TestV2Controller(),new TestLegacyController())
                .setControllerAdvice(new V2ExceptionHandler(),new ApiExceptionHandler()).build();
        mvc.perform(get("/test-v2/errors")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_STATE_CONFLICT")).andExpect(jsonPath("$.message").value("资源已更新"));
        mvc.perform(post("/test-v2/money").contentType("application/json").content("{\"amountCents\":1.5}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/test-legacy/errors").accept("application/json")).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("旧错误")).andExpect(jsonPath("$.code").doesNotExist());
    }
    @Test void realActorsAndSystemSourcesCannotBeFabricated() {
        assertThrows(IllegalArgumentException.class,()->new AuditFacts.Actor(AuditFacts.ActorKind.SYSTEM,1L,"task"));
        assertThrows(IllegalArgumentException.class,()->new AuditFacts.Actor(AuditFacts.ActorKind.SYSTEM,null,null));
        assertThrows(IllegalArgumentException.class,()->new AuditFacts.Actor(AuditFacts.ActorKind.ADMIN,null,null));
        assertEquals("2026-10-26T00:00:00Z",V2Time.format(Instant.parse("2026-10-26T08:00:00+08:00")));
    }
    @V2Api @RestController static class TestV2Controller {
        @GetMapping("/test-v2/errors") public void error(){throw new V2Exception(V2Error.ORDER_STATE_CONFLICT,"资源已更新");}
        @PostMapping("/test-v2/money") public Dto money(@RequestBody Dto dto){return dto;}
    }
    @RestController static class TestLegacyController {
        @GetMapping("/test-legacy/errors") public void error(){throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"旧错误");}
    }
}
