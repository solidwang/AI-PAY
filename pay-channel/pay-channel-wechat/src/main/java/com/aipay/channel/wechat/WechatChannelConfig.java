package com.aipay.channel.wechat;

import com.wechat.pay.java.core.RSAAutoCertificateConfig;

final class WechatChannelConfig {
    private WechatChannelConfig() {}

    static RSAAutoCertificateConfig buildConfig(String mchId, String privateKey,
                                                 String serialNo, String apiV3Key) {
        return new RSAAutoCertificateConfig.Builder()
            .merchantId(mchId)
            .privateKey(privateKey)
            .merchantSerialNumber(serialNo)
            .apiV3Key(apiV3Key)
            .build();
    }
}
