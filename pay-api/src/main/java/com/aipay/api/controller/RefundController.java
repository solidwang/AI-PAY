package com.aipay.api.controller;

import com.aipay.core.domain.App;
import com.aipay.core.domain.Charge;
import com.aipay.core.domain.Refund;
import com.aipay.core.service.ChargeService;
import com.aipay.core.service.RefundService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "Refunds", description = "Refund operations")
@RestController
@RequiredArgsConstructor
public class RefundController {

    private final ChargeService chargeService;
    private final RefundService refundService;

    @PostMapping("/v1/charges/{chargeId}/refunds")
    public ResponseEntity<Map<String, Object>> createRefund(
            @AuthenticationPrincipal App app,
            @PathVariable String chargeId,
            @RequestBody Map<String, Object> body) {
        Charge charge = chargeService.findByChargeId(chargeId);
        if (charge == null) {
            return ResponseEntity.status(404).body(Map.of("error", Map.of("code", "not_found", "message", "Charge not found")));
        }
        if (body.get("amount") == null) {
            return ResponseEntity.badRequest().body(Map.of("error", Map.of("code", "invalid_request", "message", "amount is required")));
        }
        int amount = ((Number) body.get("amount")).intValue();
        String outRefundNo = (String) body.get("out_refund_no");
        String description = (String) body.get("description");

        Refund refund = refundService.createRefund(charge, amount, outRefundNo, description);
        return ResponseEntity.ok(toRefundResponse(refund));
    }

    @GetMapping("/v1/charges/{chargeId}/refunds")
    public ResponseEntity<Map<String, Object>> listRefunds(
            @AuthenticationPrincipal App app,
            @PathVariable String chargeId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Refund> result = refundService.listByChargeId(chargeId, page, size);
        return ResponseEntity.ok(Map.of(
            "object", "list",
            "data", result.getRecords().stream().map(this::toRefundResponse).toList(),
            "total", result.getTotal()
        ));
    }

    @GetMapping("/v1/refunds/{refundId}")
    public ResponseEntity<Map<String, Object>> getRefund(
            @AuthenticationPrincipal App app,
            @PathVariable String refundId) {
        return ResponseEntity.ok(toRefundResponse(refundService.findByRefundId(refundId)));
    }

    private Map<String, Object> toRefundResponse(Refund refund) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", refund.getRefundId());
        resp.put("object", "refund");
        resp.put("charge_id", refund.getChargeId());
        resp.put("amount", refund.getAmount());
        resp.put("status", refund.getStatus());
        resp.put("out_refund_no", refund.getOutRefundNo());
        resp.put("created_at", refund.getCreatedAt());
        return resp;
    }
}
