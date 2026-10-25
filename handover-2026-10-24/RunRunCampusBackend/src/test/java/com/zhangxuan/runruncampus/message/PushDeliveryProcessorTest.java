package com.zhangxuan.runruncampus.message;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PushDeliveryProcessorTest {
    private final PushDeliveryRepository deliveries = mock(PushDeliveryRepository.class);
    private final PushNotificationGateway gateway = mock(PushNotificationGateway.class);
    private final PushDeliveryProcessor processor = new PushDeliveryProcessor(deliveries, gateway);

    @Test
    void successfulDeliveryIsMarkedSent() throws Exception {
        var task = task(0);
        when(deliveries.claimNext()).thenReturn(Optional.of(task), Optional.empty());

        processor.process();

        verify(gateway).send(task.notification());
        verify(deliveries).markSent(task.id());
        verify(deliveries, never()).markFailure(anyLong(), anyInt(), anyString(), any(), anyBoolean());
    }

    @Test
    void firstFailureSchedulesRetryWithoutLosingIdempotencyToken() throws Exception {
        var task = task(0);
        when(deliveries.claimNext()).thenReturn(Optional.of(task), Optional.empty());
        doThrow(new IllegalStateException("provider unavailable")).when(gateway).send(task.notification());

        processor.process();

        verify(deliveries).markFailure(eq(task.id()), eq(1), eq("provider unavailable"), any(Instant.class), eq(false));
        assertEquals("push-token", task.notification().idempotentToken());
    }

    @Test
    void fifthFailureBecomesDeadLetter() throws Exception {
        var task = task(4);
        when(deliveries.claimNext()).thenReturn(Optional.of(task), Optional.empty());
        doThrow(new IllegalStateException("still unavailable")).when(gateway).send(task.notification());

        processor.process();

        verify(deliveries).markFailure(eq(task.id()), eq(5), eq("still unavailable"), any(Instant.class), eq(true));
    }

    private PushDeliveryTask task(int attempts) {
        return new PushDeliveryTask(7L, new OrderPushRequested(2L, 9L, "订单更新", "请查看订单", "push-token"),
                attempts, Instant.parse("2026-09-16T00:00:00Z"));
    }
}
