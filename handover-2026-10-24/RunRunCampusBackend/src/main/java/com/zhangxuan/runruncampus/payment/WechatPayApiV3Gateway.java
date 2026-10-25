package com.zhangxuan.runruncampus.payment;

import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.app.AppServiceExtension;
import com.wechat.pay.java.service.payments.app.model.Amount;
import com.wechat.pay.java.service.payments.app.model.CloseOrderRequest;
import com.wechat.pay.java.service.payments.app.model.PrepayRequest;
import com.wechat.pay.java.service.payments.app.model.PrepayWithRequestPaymentResponse;
import com.wechat.pay.java.service.payments.app.model.QueryOrderByOutTradeNoRequest;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.QueryByOutRefundNoRequest;
import com.wechat.pay.java.service.refund.model.Refund;
import com.wechat.pay.java.service.refund.model.RefundNotification;
import com.wechat.pay.java.service.refund.model.Status;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.nio.file.Files;
import java.nio.file.Path;

final class WechatPayApiV3Gateway implements WechatPayGateway {
    private final WechatPayProperties properties;
    private final AppServiceExtension appService;
    private final RefundService refundService;
    private final NotificationParser notificationParser;

    WechatPayApiV3Gateway(WechatPayProperties properties) {
        String apiV3Key = resolveApiV3Key(properties);
        requireConfigured(properties, apiV3Key);
        this.properties = properties;
        Config config = new RSAAutoCertificateConfig.Builder()
                .merchantId(properties.merchantId())
                .privateKeyFromPath(properties.privateKeyPath())
                .merchantSerialNumber(properties.merchantSerialNumber())
                .apiV3Key(apiV3Key)
                .build();
        this.appService = new AppServiceExtension.Builder().config(config).build();
        this.refundService = new RefundService.Builder().config(config).build();
        this.notificationParser = new NotificationParser((RSAAutoCertificateConfig) config);
    }

    @Override
    public AppPayParameters prepay(String outTradeNo, String description, int amountCents, String attach,
                                   Instant expiresAt) {
        try {
            PrepayRequest request = new PrepayRequest();
            request.setAppid(properties.appId());
            request.setMchid(properties.merchantId());
            request.setDescription(description);
            request.setOutTradeNo(outTradeNo);
            request.setTimeExpire(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(expiresAt.atOffset(ZoneOffset.ofHours(8))));
            request.setAttach(attach);
            request.setNotifyUrl(properties.notifyUrl());
            Amount amount = new Amount();
            amount.setTotal(amountCents);
            amount.setCurrency("CNY");
            request.setAmount(amount);
            PrepayWithRequestPaymentResponse response = appService.prepayWithRequestPayment(request);
            return new AppPayParameters(response.getAppid(), response.getPartnerId(), response.getPrepayId(),
                    response.getPackageVal(), response.getNonceStr(), response.getTimestamp(), response.getSign());
        } catch (RuntimeException exception) {
            throw upstream("微信支付下单失败", exception);
        }
    }

    @Override
    public TradeResult query(String outTradeNo) {
        try {
            QueryOrderByOutTradeNoRequest request = new QueryOrderByOutTradeNoRequest();
            request.setMchid(properties.merchantId());
            request.setOutTradeNo(outTradeNo);
            return trade(appService.queryOrderByOutTradeNo(request));
        } catch (RuntimeException exception) {
            throw upstream("微信支付状态查询失败", exception);
        }
    }

    @Override
    public void close(String outTradeNo) {
        try {
            CloseOrderRequest request = new CloseOrderRequest();
            request.setMchid(properties.merchantId());
            request.setOutTradeNo(outTradeNo);
            appService.closeOrder(request);
        } catch (RuntimeException exception) {
            throw upstream("微信支付订单关闭失败", exception);
        }
    }

    @Override
    public RefundResult refund(String outTradeNo, String outRefundNo, int totalCents, int refundCents, String reason) {
        try {
            CreateRequest request = new CreateRequest();
            request.setOutTradeNo(outTradeNo);
            request.setOutRefundNo(outRefundNo);
            request.setReason(reason);
            request.setNotifyUrl(properties.refundNotifyUrl());
            AmountReq amount = new AmountReq();
            amount.setTotal((long) totalCents);
            amount.setRefund((long) refundCents);
            amount.setCurrency("CNY");
            request.setAmount(amount);
            return refund(refundService.create(request));
        } catch (RuntimeException exception) {
            throw upstream("微信退款申请失败", exception);
        }
    }

    @Override
    public RefundResult queryRefund(String outRefundNo) {
        try {
            QueryByOutRefundNoRequest request = new QueryByOutRefundNoRequest();
            request.setOutRefundNo(outRefundNo);
            return refund(refundService.queryByOutRefundNo(request));
        } catch (RuntimeException exception) {
            throw upstream("微信退款状态查询失败", exception);
        }
    }

    @Override
    public TradeResult parsePaymentNotification(NotificationHeaders headers, String body) {
        try {
            RequestParam request = requestParam(headers, body);
            return trade(notificationParser.parse(request, Transaction.class));
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "微信支付通知验签失败", exception);
        }
    }

    @Override
    public RefundNotificationResult parseRefundNotification(NotificationHeaders headers, String body) {
        try {
            RefundNotification notification = notificationParser.parse(requestParam(headers, body), RefundNotification.class);
            int amount = notification.getAmount() == null || notification.getAmount().getRefund() == null
                    ? -1 : Math.toIntExact(notification.getAmount().getRefund());
            RefundStatus status = notification.getRefundStatus() == Status.SUCCESS
                    ? RefundStatus.SUCCESS : RefundStatus.PROCESSING;
            return new RefundNotificationResult(notification.getOutTradeNo(), notification.getOutRefundNo(),
                    notification.getRefundId(), amount, status);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "微信退款通知验签失败", exception);
        }
    }

    private RequestParam requestParam(NotificationHeaders headers, String body) {
        return new RequestParam.Builder()
                .serialNumber(headers.serial()).timestamp(headers.timestamp()).nonce(headers.nonce())
                .signature(headers.signature()).signType(headers.signType()).body(body).build();
    }

    private TradeResult trade(Transaction transaction) {
        int total = transaction.getAmount() == null || transaction.getAmount().getTotal() == null
                ? -1 : transaction.getAmount().getTotal();
        return new TradeResult(transaction.getAppid(), transaction.getMchid(), transaction.getOutTradeNo(),
                transaction.getTransactionId(), total, transaction.getTradeState() == Transaction.TradeStateEnum.SUCCESS);
    }

    private RefundResult refund(Refund refund) {
        RefundStatus status = refund.getStatus() == Status.SUCCESS ? RefundStatus.SUCCESS : RefundStatus.PROCESSING;
        return new RefundResult(refund.getRefundId(), status);
    }

    private ResponseStatusException upstream(String message, RuntimeException cause) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, message, cause);
    }

    private static void requireConfigured(WechatPayProperties properties, String apiV3Key) {
        if (blank(properties.appId()) || blank(properties.merchantId()) || blank(properties.merchantSerialNumber())
                || blank(properties.privateKeyPath()) || blank(apiV3Key) || blank(properties.notifyUrl())
                || blank(properties.refundNotifyUrl())) {
            throw new IllegalStateException("微信支付已启用，但商户配置不完整");
        }
        if (!properties.notifyUrl().startsWith("https://") || !properties.refundNotifyUrl().startsWith("https://")) {
            throw new IllegalStateException("微信支付回调地址必须使用 HTTPS");
        }
        if (apiV3Key.length() != 32) throw new IllegalStateException("微信支付 APIv3 密钥必须为 32 字节");
    }

    private static String resolveApiV3Key(WechatPayProperties properties) {
        if (!blank(properties.apiV3Key())) return properties.apiV3Key();
        if (blank(properties.apiV3KeyPath())) return null;
        try {
            return Files.readString(Path.of(properties.apiV3KeyPath())).trim();
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取微信支付 APIv3 密钥文件", exception);
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
