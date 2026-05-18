package com.aipay.api.controller;

import com.aipay.core.domain.App;
import com.aipay.core.domain.Charge;
import com.aipay.core.service.ChargeService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "Charges", description = "Payment order operations")
@RestController
@RequestMapping("/v1/charges")
@RequiredArgsConstructor
public class ChargeController {

    private final ChargeService chargeService;
    private final ObjectMapper objectMapper;

    @Operation(summary = "Create a payment charge")
    @PostMapping
    public ResponseEntity<Map<String, Object>> createCharge(
            @AuthenticationPrincipal App app,
            @RequestBody Map<String, Object> body) {
        String outTradeNo = (String) body.get("order_no");
        String channel = (String) body.get("channel");
        if (body.get("amount") == null) {
            return ResponseEntity.badRequest().body(Map.of("error", Map.of("code", "invalid_request", "message", "amount is required")));
        }
        int amount = ((Number) body.get("amount")).intValue();
        String currency = (String) body.getOrDefault("currency", "cny");
        String subject = (String) body.get("subject");
        String bodyText = (String) body.get("body");
        String clientIp = (String) body.get("client_ip");
        long timeExpire = ((Number) body.getOrDefault("time_expire", 1800)).longValue();
        Map<String, Object> channelExtra = (Map<String, Object>) body.get("channel_extra");
        Map<String, Object> metadata = (Map<String, Object>) body.get("metadata");

        Charge charge = chargeService.createCharge(app, outTradeNo, channel, amount,
            currency, subject, bodyText, clientIp, timeExpire, channelExtra, metadata);

        return ResponseEntity.ok(toChargeResponse(charge));
    }

    @Operation(summary = "Get a charge by ID")
    @GetMapping("/{chargeId}")
    public ResponseEntity<Map<String, Object>> getCharge(
            @AuthenticationPrincipal App app,
            @PathVariable String chargeId) {
        Charge charge = chargeService.findByChargeId(chargeId);
        if (charge == null) {
            return ResponseEntity.status(404).body(Map.of("error", Map.of("code", "not_found", "message", "Charge not found")));
        }
        if (!app.getId().equals(charge.getAppId())) {
            return ResponseEntity.status(403).body(Map.of("error", Map.of("code", "forbidden", "message", "Access denied")));
        }
        return ResponseEntity.ok(toChargeResponse(charge));
    }

    @Operation(summary = "List charges with pagination")
    @GetMapping
    public ResponseEntity<Map<String, Object>> listCharges(
            @AuthenticationPrincipal App app,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        Page<Charge> result = chargeService.listCharges(app.getId(), null, status, page, size);
        return ResponseEntity.ok(Map.of(
            "object", "list",
            "data", result.getRecords().stream().map(this::toChargeResponse).toList(),
            "total", result.getTotal(),
            "page", page,
            "size", size
        ));
    }

    @SneakyThrows
    private Map<String, Object> toChargeResponse(Charge charge) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", charge.getChargeId());
        resp.put("object", "charge");
        resp.put("status", charge.getStatus());
        resp.put("paid", charge.getPaid() == 1);
        resp.put("amount", charge.getAmount());
        resp.put("currency", charge.getCurrency());
        resp.put("subject", charge.getSubject());
        resp.put("out_trade_no", charge.getOutTradeNo());
        resp.put("channel", charge.getChannel());
        resp.put("created_at", charge.getCreatedAt());
        if (charge.getCredential() != null) {
            resp.put("credential", objectMapper.readValue(charge.getCredential(), Map.class));
        }
        if (charge.getFailureCode() != null) {
            resp.put("failure_code", charge.getFailureCode());
            resp.put("failure_msg", charge.getFailureMsg());
        }
        return resp;
    }
}
