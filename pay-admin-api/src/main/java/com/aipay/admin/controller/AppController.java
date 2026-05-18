package com.aipay.admin.controller;

import com.aipay.core.service.AppService;
import com.aipay.core.service.ChannelConfigService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Admin Apps & Channels")
@RestController
@RequestMapping("/admin/v1/apps")
@RequiredArgsConstructor
public class AppController {

    private final AppService appService;
    private final ChannelConfigService channelConfigService;

    @GetMapping("/{appId}")
    public ResponseEntity<?> getApp(@PathVariable String appId) {
        return ResponseEntity.ok(appService.findByAppId(appId));
    }

    @GetMapping("/{appId}/channels/{channel}")
    public ResponseEntity<?> getChannelConfig(@PathVariable String appId,
                                               @PathVariable String channel) {
        var app = appService.findByAppId(appId);
        var cfg = channelConfigService.findActiveConfig(app.getId(), channel);
        if (cfg == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("app_id", appId, "channel", channel,
            "status", cfg.getStatus()));
    }

    @PutMapping("/{appId}/channels/{channel}")
    public ResponseEntity<?> updateChannelConfig(@PathVariable String appId,
                                                  @PathVariable String channel,
                                                  @RequestBody Map<String, Object> body) {
        var app = appService.findByAppId(appId);
        channelConfigService.saveConfig(app.getId(), channel, body);
        return ResponseEntity.ok(Map.of("result", "updated"));
    }
}
