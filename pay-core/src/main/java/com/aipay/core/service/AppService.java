package com.aipay.core.service;

import com.aipay.common.exception.BizException;
import com.aipay.common.exception.ErrorCode;
import com.aipay.common.util.CryptoUtil;
import com.aipay.common.util.IdGenerator;
import com.aipay.core.domain.App;
import com.aipay.core.mapper.AppMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AppService {

    private final AppMapper appMapper;

    public Map<String, Object> createApp(Long merchantId, String name) {
        String plainLiveKey = IdGenerator.liveApiKey();
        String plainTestKey = IdGenerator.testApiKey();

        App app = new App();
        app.setAppId(IdGenerator.appId());
        app.setMerchantId(merchantId);
        app.setName(name);
        app.setLiveKey(CryptoUtil.sha256(plainLiveKey));
        app.setTestKey(CryptoUtil.sha256(plainTestKey));
        app.setStatus(1);
        appMapper.insert(app);

        return Map.of(
            "app", app,
            "live_key", plainLiveKey,
            "test_key", plainTestKey
        );
    }

    public App authenticateApiKey(String rawKey) {
        String hashed = CryptoUtil.sha256(rawKey);
        App app = appMapper.selectOne(new LambdaQueryWrapper<App>()
            .eq(App::getLiveKey, hashed).eq(App::getStatus, 1));
        if (app == null) {
            app = appMapper.selectOne(new LambdaQueryWrapper<App>()
                .eq(App::getTestKey, hashed).eq(App::getStatus, 1));
        }
        return app;
    }

    public App findByAppId(String appId) {
        App app = appMapper.selectByAppId(appId);
        if (app == null) throw new BizException(ErrorCode.APP_NOT_FOUND);
        return app;
    }

    public List<App> listByMerchant(Long merchantId) {
        return appMapper.selectList(new LambdaQueryWrapper<App>()
            .eq(App::getMerchantId, merchantId));
    }
}
