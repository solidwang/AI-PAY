package com.aipay.channel.api.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RefundRequest {
    private String chargeId;
    private String outRefundNo;
    private String transactionNo;
    private int totalAmount;
    private int refundAmount;
    private String description;
    // WeChat credentials
    private String wechatAppId;
    private String mchId;
    private String apiV3Key;
    private String serialNo;
    private String privateKey;
    // Alipay credentials
    private String alipayAppId;
    private String alipayPrivateKey;
    private String alipayPublicKey;
    private String outTradeNo;
}
