package com.aipay.api.controller;

import com.aipay.api.security.ApiKeySecurityConfig;
import com.aipay.core.domain.App;
import com.aipay.core.domain.Charge;
import com.aipay.core.service.AppService;
import com.aipay.core.service.ChargeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChargeController.class)
@Import(ApiKeySecurityConfig.class)
class ChargeControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean AppService appService;
    @MockBean ChargeService chargeService;

    private String basicAuthHeader(String key) {
        return "Basic " + Base64.getEncoder().encodeToString((key + ":").getBytes());
    }

    @Test
    void createCharge_withValidApiKey_returns200() throws Exception {
        App app = new App();
        app.setId(1L);
        app.setAppId("app_test");
        app.setMerchantId(100L);

        Charge charge = new Charge();
        charge.setChargeId("ch_abc123");
        charge.setStatus("pending");
        charge.setPaid(0);
        charge.setAmount(9900);
        charge.setCurrency("cny");
        charge.setCredential("{\"wechat_jsapi\":{\"appId\":\"wxabc\"}}");

        when(appService.authenticateApiKey("sk_live_testkey")).thenReturn(app);
        when(chargeService.createCharge(any(), any(), any(), anyInt(), any(), any(),
            any(), any(), anyLong(), any(), any())).thenReturn(charge);

        Map<String, Object> body = Map.of(
            "order_no", "ORDER_001",
            "channel", "wechat_jsapi",
            "amount", 9900,
            "currency", "cny",
            "subject", "Test Product",
            "time_expire", 1800,
            "channel_extra", Map.of("open_id", "oUpF8xxx")
        );

        mockMvc.perform(post("/v1/charges")
                .header("Authorization", basicAuthHeader("sk_live_testkey"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("ch_abc123"))
            .andExpect(jsonPath("$.status").value("pending"));
    }

    @Test
    void createCharge_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(post("/v1/charges")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }
}
