package com.aipay.admin.controller;

import com.aipay.core.service.ChargeService;
import com.aipay.core.service.RefundService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin Charges & Refunds")
@RestController
@RequiredArgsConstructor
public class ChargeQueryController {

    private final ChargeService chargeService;
    private final RefundService refundService;

    @GetMapping("/admin/v1/charges")
    public ResponseEntity<?> listCharges(
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(chargeService.listCharges(null, merchantId, status, page, size));
    }

    @GetMapping("/admin/v1/charges/{chargeId}")
    public ResponseEntity<?> getCharge(@PathVariable String chargeId) {
        return ResponseEntity.ok(chargeService.findByChargeId(chargeId));
    }

    @GetMapping("/admin/v1/refunds")
    public ResponseEntity<?> listRefunds(
            @RequestParam String chargeId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(refundService.listByChargeId(chargeId, page, size));
    }
}
