package com.aipay.core.service;

import com.aipay.channel.api.ChannelRouter;
import com.aipay.channel.api.model.CreateOrderRequest;
import com.aipay.channel.api.model.CreateOrderResult;
import com.aipay.common.enums.ChargeStatus;
import com.aipay.common.exception.BizException;
import com.aipay.common.exception.ErrorCode;
import com.aipay.common.util.IdGenerator;
import com.aipay.core.domain.App;
import com.aipay.core.domain.Charge;
import com.aipay.core.domain.ChannelConfig;
import com.aipay.core.mapper.ChargeMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChargeService {

    private final ChargeMapper chargeMapper;
    private final ChannelConfigService channelConfigService;
    private final ChannelRouter channelRouter;
    private final ObjectMapper objectMapper;

    @Transactional
    public Charge createCharge(App app, String outTradeNo, String channel,
                                int amount, String currency, String subject, String body,
                                String clientIp, long timeExpireSeconds,
                                Map<String, Object> channelExtra,
                                Map<String, Object> metadata) {
        ChannelConfig channelConfig = channelConfigService.findActiveConfig(app.getId(), channel);
        if (channelConfig == null) {
            throw new BizException(ErrorCode.CHANNEL_NOT_CONFIGURED);
        }

        Charge charge = new Charge();
        charge.setChargeId(IdGenerator.chargeId());
        charge.setAppId(app.getId());
        charge.setMerchantId(app.getMerchantId());
        charge.setOutTradeNo(outTradeNo);
        charge.setChannel(channel);
        charge.setAmount(amount);
        charge.setCurrency(currency);
        charge.setSubject(subject);
        charge.setBody(body);
        charge.setClientIp(clientIp);
        charge.setStatus(ChargeStatus.created.name());
        charge.setPaid(0);
        charge.setAmountRefunded(0);
        charge.setTimeExpire(LocalDateTime.now().plusSeconds(timeExpireSeconds));
        charge.setChannelExtra(toJson(channelExtra));
        charge.setMetadata(toJson(metadata));
        chargeMapper.insert(charge);

        CreateOrderRequest request = channelConfigService.buildCreateOrderRequest(
            app, channelConfig, charge);
        CreateOrderResult result = channelRouter.route(channel).createOrder(request);

        if (result.isSuccess()) {
            charge.setStatus(ChargeStatus.pending.name());
            charge.setCredential(toJson(result.getCredential()));
            charge.setTransactionNo(result.getTransactionNo());
        } else {
            charge.setStatus(ChargeStatus.closed.name());
            charge.setFailureCode(result.getFailureCode());
            charge.setFailureMsg(result.getFailureMsg());
        }
        chargeMapper.updateById(charge);

        return charge;
    }

    public Charge findByChargeId(String chargeId) {
        Charge charge = chargeMapper.selectByChargeId(chargeId);
        if (charge == null) throw new BizException(ErrorCode.CHARGE_NOT_FOUND);
        return charge;
    }

    public Page<Charge> listCharges(Long appId, Long merchantId, String status,
                                     int page, int size) {
        LambdaQueryWrapper<Charge> wrapper = new LambdaQueryWrapper<Charge>()
            .eq(appId != null, Charge::getAppId, appId)
            .eq(merchantId != null, Charge::getMerchantId, merchantId)
            .eq(status != null, Charge::getStatus, status)
            .orderByDesc(Charge::getCreatedAt);
        return chargeMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @SneakyThrows
    private String toJson(Object obj) {
        if (obj == null) return null;
        return objectMapper.writeValueAsString(obj);
    }
}
