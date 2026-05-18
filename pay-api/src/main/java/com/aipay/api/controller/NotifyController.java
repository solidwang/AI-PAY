package com.aipay.api.controller;

import com.aipay.common.constant.ChannelCode;
import com.aipay.core.domain.App;
import com.aipay.core.domain.ChannelConfig;
import com.aipay.core.service.AppService;
import com.aipay.core.service.ChannelConfigService;
import com.aipay.core.service.NotifyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Tag(name = "Notify", description = "Channel async notifications")
@RestController
@RequestMapping("/v1/notify")
@RequiredArgsConstructor
public class NotifyController {

    private final NotifyService notifyService;
    private final AppService appService;
    private final ChannelConfigService channelConfigService;

    @PostMapping("/wechat/{appId}")
    public ResponseEntity<Map<String, String>> wechatNotify(
            @PathVariable String appId,
            @RequestParam(required = false) String channel,
            HttpServletRequest request) throws IOException {
        String rawBody = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> headers = extractHeaders(request);

        App app = appService.findByAppId(appId);
        String channelCode = channel != null ? channel : ChannelCode.WECHAT_JSAPI;
        ChannelConfig cfg = channelConfigService.findActiveConfig(app.getId(), channelCode);
        Map<String, Object> creds = channelConfigService.decryptConfig(cfg);

        String transactionNo = headers.getOrDefault("Wechatpay-Nonce", "unknown");

        try {
            notifyService.processNotify(channelCode, transactionNo, rawBody, headers, null,
                (String) creds.get("mchId"), (String) creds.get("apiV3Key"),
                (String) creds.get("serialNo"), (String) creds.get("privateKey"), null);
            return ResponseEntity.ok(Map.of("code", "SUCCESS", "message", "成功"));
        } catch (Exception e) {
            log.error("WeChat notify processing failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(Map.of("code", "FAIL", "message", e.getMessage()));
        }
    }

    @PostMapping("/alipay/{appId}")
    public ResponseEntity<String> alipayNotify(
            @PathVariable String appId,
            HttpServletRequest request) {
        App app = appService.findByAppId(appId);
        ChannelConfig cfg = channelConfigService.findActiveConfig(app.getId(), ChannelCode.ALIPAY_WAP);
        Map<String, Object> creds = channelConfigService.decryptConfig(cfg);

        String transactionNo = request.getParameter("trade_no");
        String rawBody = request.getParameterMap().toString();
        Map<String, String[]> params = request.getParameterMap();

        try {
            notifyService.processNotify(ChannelCode.ALIPAY_WAP, transactionNo, rawBody, null,
                params, null, null, null, null, (String) creds.get("alipayPublicKey"));
            return ResponseEntity.ok("success");
        } catch (Exception e) {
            log.error("Alipay notify processing failed: {}", e.getMessage(), e);
            return ResponseEntity.ok("fail");
        }
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        return headers;
    }
}
