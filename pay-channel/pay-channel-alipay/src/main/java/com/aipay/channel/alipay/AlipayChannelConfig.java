package com.aipay.channel.alipay;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;

final class AlipayChannelConfig {
    private AlipayChannelConfig() {}

    static AlipayClient buildClient(String appId, String privateKey, String alipayPublicKey) {
        try {
            return new DefaultAlipayClient(
                "https://openapi.alipay.com/gateway.do",
                appId,
                privateKey,
                "json",
                "UTF-8",
                alipayPublicKey,
                "RSA2"
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Alipay client", e);
        }
    }
}
