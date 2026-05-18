package com.aipay.core.service;

import com.aipay.channel.api.model.CreateOrderRequest;
import com.aipay.common.util.CryptoUtil;
import com.aipay.core.domain.App;
import com.aipay.core.domain.ChannelConfig;
import com.aipay.core.domain.Charge;
import com.aipay.core.mapper.ChannelConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelConfigService {

    private final ChannelConfigMapper channelConfigMapper;
    private final ObjectMapper objectMapper;

    @Value("${app.encrypt-key}")
    private String encryptKey;

    @Value("${app.notify-base-url}")
    private String notifyBaseUrl;

    public ChannelConfig findActiveConfig(long appId, String channel) {
        return channelConfigMapper.selectOne(new LambdaQueryWrapper<ChannelConfig>()
            .eq(ChannelConfig::getAppId, appId)
            .eq(ChannelConfig::getChannel, channel)
            .eq(ChannelConfig::getStatus, 1));
    }

    public Map<String, Object> decryptConfig(ChannelConfig config) {
        try {
            String json = CryptoUtil.decrypt(config.getConfigJson(), encryptKey);
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt channel config for app " + config.getAppId(), e);
        }
    }

    public void saveConfig(long appId, String channel, Map<String, Object> configMap) {
        try {
            String json = objectMapper.writeValueAsString(configMap);
            String encrypted = CryptoUtil.encrypt(json, encryptKey);

            ChannelConfig existing = findActiveConfig(appId, channel);
            if (existing != null) {
                existing.setConfigJson(encrypted);
                channelConfigMapper.updateById(existing);
            } else {
                ChannelConfig cc = new ChannelConfig();
                cc.setAppId(appId);
                cc.setChannel(channel);
                cc.setConfigJson(encrypted);
                cc.setStatus(1);
                channelConfigMapper.insert(cc);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to save channel config", e);
        }
    }

    public CreateOrderRequest buildCreateOrderRequest(App app, ChannelConfig channelConfig, Charge charge) {
        Map<String, Object> cfg = decryptConfig(channelConfig);
        String channel = channelConfig.getChannel();
        String notifyUrl = notifyBaseUrl + "/v1/notify/" +
            (channel.startsWith("wechat") ? "wechat" : "alipay") + "/" + app.getAppId();

        Map<String, Object> extra = charge.getChannelExtra() != null
            ? parseJson(charge.getChannelExtra())
            : Map.of();

        CreateOrderRequest.CreateOrderRequestBuilder builder = CreateOrderRequest.builder()
            .chargeId(charge.getChargeId())
            .outTradeNo(charge.getOutTradeNo())
            .amount(charge.getAmount())
            .currency(charge.getCurrency())
            .subject(charge.getSubject())
            .body(charge.getBody())
            .clientIp(charge.getClientIp())
            .notifyUrl(notifyUrl)
            .channelExtra(extra);

        if (channel.startsWith("wechat")) {
            builder.wechatAppId((String) cfg.get("appId"))
                   .mchId((String) cfg.get("mchId"))
                   .apiV3Key((String) cfg.get("apiV3Key"))
                   .serialNo((String) cfg.get("serialNo"))
                   .privateKey((String) cfg.get("privateKey"));
        } else if (channel.startsWith("alipay")) {
            builder.alipayAppId((String) cfg.get("appId"))
                   .alipayPrivateKey((String) cfg.get("privateKey"))
                   .alipayPublicKey((String) cfg.get("alipayPublicKey"))
                   .returnUrl((String) extra.get("return_url"));
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
