package com.aipay.channel.wechat;

import com.aipay.channel.api.PayChannel;
import com.aipay.channel.api.model.*;
import com.aipay.common.constant.ChannelCode;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class WechatNativeChannel implements PayChannel {

    @Override
    public String channelCode() {
        return ChannelCode.WECHAT_NATIVE;
    }

    @Override
    public CreateOrderResult createOrder(CreateOrderRequest req) {
        try {
            RSAAutoCertificateConfig config = WechatChannelConfig.buildConfig(
                req.getMchId(), req.getPrivateKey(), req.getSerialNo(), req.getApiV3Key());

            NativePayService service = new NativePayService.Builder().config(config).build();

            PrepayRequest request = new PrepayRequest();
            request.setAppid(req.getWechatAppId());
            request.setMchid(req.getMchId());
            request.setDescription(req.getSubject());
            request.setOutTradeNo(req.getOutTradeNo());
            request.setNotifyUrl(req.getNotifyUrl());

            Amount amount = new Amount();
            amount.setTotal(req.getAmount());
            request.setAmount(amount);

            PrepayResponse response = service.prepay(request);

            return CreateOrderResult.builder()
                .success(true)
                .credential(Map.of(ChannelCode.WECHAT_NATIVE,
                    Map.of("code_url", response.getCodeUrl())))
                .build();

        } catch (Exception e) {
            log.error("WeChat Native createOrder failed: {}", e.getMessage(), e);
            return CreateOrderResult.builder()
                .success(false).failureCode("WECHAT_NATIVE_ERROR").failureMsg(e.getMessage())
                .build();
        }
    }

    @Override
    public NotifyResult parseNotify(NotifyRequest req) {
        return new WechatJsapiChannel().parseNotify(req);
    }

    @Override
    public RefundResult refund(RefundRequest req) {
        return new WechatJsapiChannel().refund(req);
    }

    @Override
    public QueryResult query(String outTradeNo) {
        log.warn("WeChat query not implemented for outTradeNo={}", outTradeNo);
        return QueryResult.builder().paid(false).build();
    }
}
