package com.aipay.admin.controller;

import com.aipay.core.service.AppService;
import com.aipay.core.service.MerchantService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Admin Merchants")
@RestController
@RequestMapping("/admin/v1/merchants")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;
    private final AppService appService;

    @GetMapping
    public ResponseEntity<?> listMerchants(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(merchantService.listMerchants(page, size));
    }

    @PostMapping
    public ResponseEntity<?> createMerchant(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(merchantService.createMerchant(
            body.get("name"), body.get("contact_email"), body.get("contact_phone")));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getMerchant(@PathVariable Long id) {
        return ResponseEntity.ok(merchantService.findById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateMerchant(@PathVariable Long id,
                                              @RequestBody Map<String, Object> body) {
        merchantService.updateMerchant(id, (String) body.get("name"),
            (String) body.get("contact_email"), (String) body.get("contact_phone"),
            body.containsKey("status") ? ((Number) body.get("status")).intValue() : null);
        return ResponseEntity.ok(merchantService.findById(id));
    }

    @GetMapping("/{id}/apps")
    public ResponseEntity<?> listApps(@PathVariable Long id) {
        return ResponseEntity.ok(appService.listByMerchant(id));
    }

    @PostMapping("/{id}/apps")
    public ResponseEntity<?> createApp(@PathVariable Long id,
                                        @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(appService.createApp(id, body.get("name")));
    }
}
