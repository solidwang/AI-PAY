package com.aipay.channel.alipay;

import com.aipay.channel.api.PayChannel;
import com.aipay.channel.api.model.*;
import com.aipay.common.constant.ChannelCode;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.domain.AlipayTradeWapPayModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradeRefundResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class AlipayWapChannel implements PayChannel {

    @Override
    public String channelCode() {
        return ChannelCode.ALIPAY_WAP;
    }

    @Override
    public CreateOrderResult createOrder(CreateOrderRequest req) {
        try {
            AlipayClient client = AlipayChannelConfig.buildClient(
                req.getAlipayAppId(), req.getAlipayPrivateKey(), req.getAlipayPublicKey());

            AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest();
            request.setReturnUrl(req.getReturnUrl());
            request.setNotifyUrl(req.getNotifyUrl());

            AlipayTradeWapPayModel model = new AlipayTradeWapPayModel();
            model.setOutTradeNo(req.getOutTradeNo());
            model.setTotalAmount(String.format("%.2f", req.getAmount() / 100.0));
            model.setSubject(req.getSubject());
            model.setBody(req.getBody());
            model.setProductCode("QUICK_WAP_WAY");
            request.setBizModel(model);

            String form = client.pageExecute(request).getBody();

            return CreateOrderResult.builder()
                .success(true)
                .credential(Map.of(ChannelCode.ALIPAY_WAP, Map.of("form", form)))
                .build();

        } catch (AlipayApiException e) {
            log.error("Alipay WAP createOrder failed: {}", e.getMessage(), e);
            return CreateOrderResult.builder()
                .success(false)
                .failureCode("ALIPAY_API_ERROR")
                .failureMsg(e.getMessage())
                .build();
        }
    }

    @Override
    public NotifyResult parseNotify(NotifyRequest req) {
        try {
            Map<String, String> params = new HashMap<>();
            for (Map.Entry<String, String[]> entry : req.getParams().entrySet()) {
                params.put(entry.getKey(), String.join(",", entry.getValue()));
            }

            boolean signValid = AlipaySignature.rsaCheckV1(
                params, req.getAlipayPublicKey(), "UTF-8", "RSA2");

            if (!signValid) {
                return NotifyResult.builder()
                    .success(false)
                    .failureReason("Alipay signature verification failed")
                    .build();
            }

            String tradeStatus = params.get("trade_status");
            if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
                return NotifyResult.builder()
                    .success(false)
                    .failureReason("Trade not completed: " + tradeStatus)
                    .build();
            }

            int paidAmount = (int) (Double.parseDouble(params.get("total_amount")) * 100);

            return NotifyResult.builder()
                .success(true)
                .transactionNo(params.get("trade_no"))
                .outTradeNo(params.get("out_trade_no"))
                .paidAmount(paidAmount)
                .build();

        } catch (Exception e) {
            log.error("Alipay parseNotify failed: {}", e.getMessage(), e);
            return NotifyResult.builder()
                .success(false).failureReason(e.getMessage())
                .build();
        }
    }

    @Override
    public RefundResult refund(RefundRequest req) {
        try {
            AlipayClient client = AlipayChannelConfig.buildClient(
                req.getAlipayAppId(), req.getAlipayPrivateKey(), req.getAlipayPublicKey());

            AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
            AlipayTradeRefundModel model = new AlipayTradeRefundModel();
            model.setTradeNo(req.getTransactionNo());
            model.setOutTradeNo(req.getOutTradeNo());
            model.setRefundAmount(String.format("%.2f", req.getRefundAmount() / 100.0));
            model.setRefundReason(req.getDescription());
            model.setOutRequestNo(req.getOutRefundNo());
            request.setBizModel(model);

            AlipayTradeRefundResponse response = client.execute(request);

            if (response.isSuccess()) {
                return RefundResult.builder()
                    .success(true)
                    .transactionNo(response.getTradeNo())
                    .build();
            } else {
                return RefundResult.builder()
                    .success(false)
                    .failureCode(response.getCode())
                    .failureMsg(response.getMsg() + ": " + response.getSubMsg())
                    .build();
            }

        } catch (AlipayApiException e) {
            log.error("Alipay refund failed: {}", e.getMessage(), e);
            return RefundResult.builder()
                .success(false).failureCode("ALIPAY_REFUND_ERROR").failureMsg(e.getMessage())
                .build();
        }
    }

    @Override
    public QueryResult query(String outTradeNo) {
        return QueryResult.builder().paid(false).build();
    }
}
