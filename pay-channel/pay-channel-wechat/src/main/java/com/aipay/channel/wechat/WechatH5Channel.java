package com.aipay.channel.wechat;

import com.aipay.channel.api.PayChannel;
import com.aipay.channel.api.model.*;
import com.aipay.common.constant.ChannelCode;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.service.payments.h5.H5Service;
import com.wechat.pay.java.service.payments.h5.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class WechatH5Channel implements PayChannel {

    @Override
    public String channelCode() {
        return ChannelCode.WECHAT_H5;
    }

    @Override
    public CreateOrderResult createOrder(CreateOrderRequest req) {
        try {
            RSAAutoCertificateConfig config = WechatChannelConfig.buildConfig(
                req.getMchId(), req.getPrivateKey(), req.getSerialNo(), req.getApiV3Key());

            H5Service service = new H5Service.Builder().config(config).build();

            PrepayRequest request = new PrepayRequest();
            request.setAppid(req.getWechatAppId());
            request.setMchid(req.getMchId());
            request.setDescription(req.getSubject());
            request.setOutTradeNo(req.getOutTradeNo());
            request.setNotifyUrl(req.getNotifyUrl());

            com.wechat.pay.java.service.payments.h5.model.Amount amount =
                new com.wechat.pay.java.service.payments.h5.model.Amount();
            amount.setTotal(req.getAmount());
            request.setAmount(amount);

            if (req.getChannelExtra() != null && req.getChannelExtra().containsKey("scene_info")) {
                SceneInfo sceneInfo = new SceneInfo();
                sceneInfo.setPayerClientIp(req.getClientIp());
                request.setSceneInfo(sceneInfo);
            }

            PrepayResponse response = service.prepay(request);

            return CreateOrderResult.builder()
                .success(true)
                .credential(Map.of(ChannelCode.WECHAT_H5, Map.of("h5_url", response.getH5Url())))
                .build();

        } catch (Exception e) {
            log.error("WeChat H5 createOrder failed: {}", e.getMessage(), e);
            return CreateOrderResult.builder()
                .success(false).failureCode("WECHAT_H5_ERROR").failureMsg(e.getMessage())
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
        return QueryResult.builder().paid(false).build();
    }
}
