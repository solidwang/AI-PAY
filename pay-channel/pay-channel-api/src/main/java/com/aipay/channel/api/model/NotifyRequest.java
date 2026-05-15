package com.aipay.channel.api.model;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class NotifyRequest {
    private String channel;
    private String rawBody;
    private Map<String, String> headers;
    private Map<String, String[]> params;
    // WeChat credentials
    private String mchId;
    private String apiV3Key;
    private String serialNo;
    private String privateKey;
    // Alipay credentials
    private String alipayPublicKey;
}
