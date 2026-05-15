package com.aipay.channel.api.model;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class CreateOrderRequest {
    private String chargeId;
    private String outTradeNo;
    private int amount;
    private String currency;
    private String subject;
    private String body;
    private String clientIp;
    private String notifyUrl;
    private long timeExpireSeconds;
    private Map<String, Object> channelExtra;
    // WeChat credentials (from decrypted ChannelConfig)
    private String wechatAppId;
    private String mchId;
    private String apiV3Key;
    private String serialNo;
    private String privateKey;
    // Alipay credentials
    private String alipayAppId;
    private String alipayPrivateKey;
    private String alipayPublicKey;
    private String returnUrl;
}
