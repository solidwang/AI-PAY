package com.aipay.channel.wechat;

import com.aipay.channel.api.PayChannel;
import com.aipay.channel.api.model.*;
import com.aipay.common.constant.ChannelCode;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.*;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class WechatJsapiChannel implements PayChannel {

    @Override
    public String channelCode() {
        return ChannelCode.WECHAT_JSAPI;
    }

    @Override
    public CreateOrderResult createOrder(CreateOrderRequest req) {
        try {
            RSAAutoCertificateConfig config = WechatChannelConfig.buildConfig(
                req.getMchId(), req.getPrivateKey(), req.getSerialNo(), req.getApiV3Key());

            JsapiServiceExtension service = new JsapiServiceExtension.Builder()
                .config(config).build();

            PrepayRequest request = new PrepayRequest();
            request.setAppid(req.getWechatAppId());
            request.setMchid(req.getMchId());
            request.setDescription(req.getSubject());
            request.setOutTradeNo(req.getOutTradeNo());
            request.setNotifyUrl(req.getNotifyUrl());

            com.wechat.pay.java.service.payments.jsapi.model.Amount amount =
                new com.wechat.pay.java.service.payments.jsapi.model.Amount();
            amount.setTotal(req.getAmount());
            request.setAmount(amount);

            Payer payer = new Payer();
            payer.setOpenid((String) req.getChannelExtra().get("open_id"));
            request.setPayer(payer);

            PrepayWithRequestPaymentResponse response =
                service.prepayWithRequestPayment(request);

            return CreateOrderResult.builder()
                .success(true)
                .credential(Map.of(ChannelCode.WECHAT_JSAPI, Map.of(
                    "appId",     response.getAppId(),
                    "timeStamp", response.getTimeStamp(),
                    "nonceStr",  response.getNonceStr(),
                    "package",   response.getPackageVal(),
                    "signType",  response.getSignType(),
                    "paySign",   response.getPaySign()
                )))
                .build();

        } catch (Exception e) {
            log.error("WeChat JSAPI createOrder failed: {}", e.getMessage(), e);
            return CreateOrderResult.builder()
                .success(false)
                .failureCode("WECHAT_API_ERROR")
                .failureMsg(e.getMessage())
                .build();
        }
    }

    @Override
    public NotifyResult parseNotify(NotifyRequest req) {
        try {
            RSAAutoCertificateConfig config = WechatChannelConfig.buildConfig(
                req.getMchId(), req.getPrivateKey(), req.getSerialNo(), req.getApiV3Key());

            RequestParam requestParam = new RequestParam.Builder()
                .serialNumber(req.getHeaders().get("Wechatpay-Serial"))
                .nonce(req.getHeaders().get("Wechatpay-Nonce"))
                .signature(req.getHeaders().get("Wechatpay-Signature"))
                .timestamp(req.getHeaders().get("Wechatpay-Timestamp"))
                .body(req.getRawBody())
                .build();

            NotificationParser parser = new NotificationParser(config);
            com.wechat.pay.java.service.payments.model.Transaction tx =
                parser.parse(requestParam,
                    com.wechat.pay.java.service.payments.model.Transaction.class);

            return NotifyResult.builder()
                .success(true)
                .transactionNo(tx.getTransactionId())
                .outTradeNo(tx.getOutTradeNo())
                .paidAmount(tx.getAmount().getPayerTotal())
                .build();

        } catch (Exception e) {
            log.error("WeChat JSAPI parseNotify failed: {}", e.getMessage(), e);
            return NotifyResult.builder()
                .success(false)
                .failureReason(e.getMessage())
                .build();
        }
    }

    @Override
    public RefundResult refund(RefundRequest req) {
        try {
            RSAAutoCertificateConfig config = WechatChannelConfig.buildConfig(
                req.getMchId(), req.getPrivateKey(), req.getSerialNo(), req.getApiV3Key());

            RefundService refundService = new RefundService.Builder().config(config).build();

            com.wechat.pay.java.service.refund.model.CreateRequest refundRequest =
                new com.wechat.pay.java.service.refund.model.CreateRequest();
            refundRequest.setTransactionId(req.getTransactionNo());
            refundRequest.setOutRefundNo(req.getOutRefundNo());
            refundRequest.setReason(req.getDescription());
            refundRequest.setNotifyUrl(null);

            AmountReq amountReq = new AmountReq();
            amountReq.setRefund((long) req.getRefundAmount());
            amountReq.setTotal((long) req.getTotalAmount());
            amountReq.setCurrency("CNY");
            refundRequest.setAmount(amountReq);

            Refund refund = refundService.create(refundRequest);

            return RefundResult.builder()
                .success(true)
                .transactionNo(refund.getRefundId())
                .build();

        } catch (Exception e) {
            log.error("WeChat refund failed: {}", e.getMessage(), e);
            return RefundResult.builder()
                .success(false)
                .failureCode("WECHAT_REFUND_ERROR")
                .failureMsg(e.getMessage())
                .build();
        }
    }

    @Override
    public QueryResult query(String outTradeNo) {
        return QueryResult.builder().paid(false).build();
    }
}
