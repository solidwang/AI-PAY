package com.aipay.admin.controller;

import com.aipay.admin.security.JwtTokenProvider;
import com.aipay.core.domain.Operator;
import com.aipay.core.domain.OperatorPermission;
import com.aipay.core.service.OperatorService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "Admin Auth")
@RestController
@RequestMapping("/admin/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final OperatorService operatorService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        Operator op = operatorService.authenticate(body.get("username"), body.get("password"));
        if (op == null) {
            return ResponseEntity.status(401)
                .body(Map.of("error", Map.of("code", "auth_failed", "message", "Invalid credentials")));
        }

        List<OperatorPermission> perms = operatorService.getPermissions(op.getId());
        String permSummary = perms.stream()
            .map(p -> p.getModule() + ":" + (p.getCanOperate() == 1 ? "operate" : "view"))
            .collect(Collectors.joining(","));

        String accessToken = jwtTokenProvider.generateAccessToken(
            op.getId(), op.getMerchantId(), op.getIsAdmin(), permSummary);

        return ResponseEntity.ok(Map.of(
            "access_token", accessToken,
            "token_type", "Bearer",
            "expires_in", 28800,
            "operator_id", op.getId(),
            "merchant_id", op.getMerchantId(),
            "is_admin", op.getIsAdmin() == 1
        ));
    }
}
