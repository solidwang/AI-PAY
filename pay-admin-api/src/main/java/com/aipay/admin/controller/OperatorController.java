package com.aipay.admin.controller;

import com.aipay.admin.security.JwtTokenProvider;
import com.aipay.admin.security.OperatorPrincipal;
import com.aipay.core.service.OperatorService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Admin Operators")
@RestController
@RequestMapping("/admin/v1/operators")
@RequiredArgsConstructor
public class OperatorController {

    private final OperatorService operatorService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping
    public ResponseEntity<?> createOperator(
            @AuthenticationPrincipal OperatorPrincipal principal,
            @RequestBody Map<String, Object> body) {
        if (!jwtTokenProvider.isAdmin(principal.token())) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        return ResponseEntity.ok(operatorService.createOperator(
            principal.merchantId(),
            (String) body.get("username"),
            (String) body.get("password"),
            (String) body.get("real_name"),
            Boolean.TRUE.equals(body.get("is_admin"))));
    }

    @PutMapping("/{id}/permissions")
    public ResponseEntity<?> updatePermissions(@AuthenticationPrincipal OperatorPrincipal principal,
                                                @PathVariable Long id,
                                                @RequestBody List<Map<String, Object>> perms) {
        if (!jwtTokenProvider.isAdmin(principal.token())) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        operatorService.updatePermissions(id, perms);
        return ResponseEntity.ok(Map.of("result", "updated"));
    }
}
