package com.aipay.core.service;

import com.aipay.channel.api.ChannelRouter;
import com.aipay.channel.api.PayChannel;
import com.aipay.channel.api.model.CreateOrderRequest;
import com.aipay.channel.api.model.CreateOrderResult;
import com.aipay.common.enums.ChargeStatus;
import com.aipay.common.exception.BizException;
import com.aipay.core.domain.*;
import com.aipay.core.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChargeServiceTest {

    @Mock ChargeMapper chargeMapper;
    @Mock ChannelConfigService channelConfigService;
    @Mock ChannelRouter channelRouter;
    @Mock PayChannel payChannel;
    @Mock ObjectMapper objectMapper;
    @InjectMocks ChargeService chargeService;

    private App testApp;
    private ChannelConfig testChannelConfig;

    @BeforeEach
    void setUp() {
        testApp = new App();
        testApp.setId(1L);
        testApp.setAppId("app_test123");
        testApp.setMerchantId(100L);

        testChannelConfig = new ChannelConfig();
        testChannelConfig.setAppId(1L);
        testChannelConfig.setChannel("wechat_jsapi");
        testChannelConfig.setStatus(1);
    }

    @Test
    void createCharge_channelNotConfigured_throwsBizException() {
        when(channelConfigService.findActiveConfig(1L, "wechat_jsapi"))
            .thenReturn(null);

        assertThatThrownBy(() ->
            chargeService.createCharge(testApp, "ORDER_001",
                "wechat_jsapi", 9900, "cny", "Test", null, "127.0.0.1",
                1800L, null, null)
        ).isInstanceOf(BizException.class)
         .hasMessageContaining("not configured");
    }

    @Test
    void createCharge_success_returnsChargeWithPendingStatus() throws Exception {
        when(channelConfigService.findActiveConfig(1L, "wechat_jsapi"))
            .thenReturn(testChannelConfig);
        when(channelConfigService.buildCreateOrderRequest(any(), any(), any()))
            .thenReturn(CreateOrderRequest.builder().build());
        when(channelRouter.route("wechat_jsapi")).thenReturn(payChannel);
        when(payChannel.createOrder(any())).thenReturn(
            CreateOrderResult.builder()
                .success(true)
                .credential(Map.of("wechat_jsapi", Map.of("appId", "wxabc")))
                .build()
        );
        when(chargeMapper.insert(any(Charge.class))).thenReturn(1);
        when(chargeMapper.updateById(any(Charge.class))).thenReturn(1);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        Charge result = chargeService.createCharge(testApp, "ORDER_001",
            "wechat_jsapi", 9900, "cny", "Test Product", null, "127.0.0.1",
            1800L, Map.of("open_id", "oUpF8xxx"), null);

        assertThat(result.getStatus()).isEqualTo(ChargeStatus.pending.name());
        assertThat(result.getPaid()).isEqualTo(0);
        verify(chargeMapper).insert(any(Charge.class));
        verify(chargeMapper).updateById(any(Charge.class));
    }
}
