package com.aipay.core.service;

import com.aipay.channel.api.ChannelRouter;
import com.aipay.channel.api.model.RefundRequest;
import com.aipay.channel.api.model.RefundResult;
import com.aipay.common.enums.ChargeStatus;
import com.aipay.common.enums.RefundStatus;
import com.aipay.common.exception.BizException;
import com.aipay.common.exception.ErrorCode;
import com.aipay.common.util.IdGenerator;
import com.aipay.core.domain.App;
import com.aipay.core.domain.Charge;
import com.aipay.core.domain.ChannelConfig;
import com.aipay.core.domain.Refund;
import com.aipay.core.mapper.ChargeMapper;
import com.aipay.core.mapper.RefundMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RefundService {

    private final RefundMapper refundMapper;
    private final ChargeMapper chargeMapper;
    private final ChannelRouter channelRouter;
    private final ChannelConfigService channelConfigService;
    private final RedissonClient redissonClient;

    @Transactional
    public Refund createRefund(Charge charge, int amount,
                                String outRefundNo, String description) {
        if (charge.getPaid() == null || charge.getPaid() == 0) {
            throw new BizException(ErrorCode.CHARGE_ALREADY_PAID);
        }

        int alreadyRefunded = charge.getAmountRefunded() == null ? 0 : charge.getAmountRefunded();
        if (alreadyRefunded + amount > charge.getAmount()) {
            throw new BizException(ErrorCode.REFUND_AMOUNT_EXCEEDED);
        }

        RBucket<String> bucket = redissonClient.getBucket("refund:" + outRefundNo);
        boolean acquired = bucket.setIfAbsent("processing", Duration.ofSeconds(300));
        if (!acquired) {
            return refundMapper.selectOne(new LambdaQueryWrapper<Refund>()
                .eq(Refund::getOutRefundNo, outRefundNo));
        }

        Refund refund = new Refund();
        refund.setRefundId(IdGenerator.refundId());
        refund.setChargeId(charge.getChargeId());
        refund.setAppId(charge.getAppId());
        refund.setMerchantId(charge.getMerchantId());
        refund.setOutRefundNo(outRefundNo);
        refund.setAmount(amount);
        refund.setDescription(description);
        refund.setStatus(RefundStatus.pending.name());
        refundMapper.insert(refund);

        ChannelConfig channelConfig = channelConfigService.findActiveConfig(
            charge.getAppId(), charge.getChannel());
        Map<String, Object> cfg = channelConfigService.decryptConfig(channelConfig);

        RefundRequest request = buildRefundRequest(charge, refund, cfg);
        RefundResult result = channelRouter.route(charge.getChannel()).refund(request);

        if (result.isSuccess()) {
            refund.setStatus(RefundStatus.success.name());
            refund.setTransactionNo(result.getTransactionNo());
            refund.setSucceedAt(LocalDateTime.now());
        } else {
            refund.setStatus(RefundStatus.failed.name());
            refund.setFailureCode(result.getFailureCode());
            refund.setFailureMsg(result.getFailureMsg());
        }
        refundMapper.updateById(refund);

        int newRefunded = alreadyRefunded + amount;
        charge.setAmountRefunded(newRefunded);
        if (newRefunded >= charge.getAmount()) {
            charge.setStatus(ChargeStatus.refunded.name());
        }
        chargeMapper.updateById(charge);

        return refund;
    }

    public Page<Refund> listByChargeId(String chargeId, int page, int size) {
        return refundMapper.selectPage(new Page<>(page, size),
            new LambdaQueryWrapper<Refund>()
                .eq(Refund::getChargeId, chargeId)
                .orderByDesc(Refund::getCreatedAt));
    }

    public Refund findByRefundId(String refundId) {
        Refund r = refundMapper.selectOne(
            new LambdaQueryWrapper<Refund>().eq(Refund::getRefundId, refundId));
        if (r == null) throw new BizException(ErrorCode.CHARGE_NOT_FOUND, "Refund not found");
        return r;
    }

    private RefundRequest buildRefundRequest(Charge charge, Refund refund, Map<String, Object> cfg) {
        RefundRequest.RefundRequestBuilder builder = RefundRequest.builder()
            .chargeId(charge.getChargeId())
            .outRefundNo(refund.getOutRefundNo())
            .transactionNo(charge.getTransactionNo())
            .outTradeNo(charge.getOutTradeNo())
            .totalAmount(charge.getAmount())
            .refundAmount(refund.getAmount())
            .description(refund.getDescription());

        if (charge.getChannel().startsWith("wechat")) {
            builder.wechatAppId((String) cfg.get("appId"))
                   .mchId((String) cfg.get("mchId"))
                   .apiV3Key((String) cfg.get("apiV3Key"))
                   .serialNo((String) cfg.get("serialNo"))
                   .privateKey((String) cfg.get("privateKey"));
        } else {
            builder.alipayAppId((String) cfg.get("appId"))
                   .alipayPrivateKey((String) cfg.get("privateKey"))
                   .alipayPublicKey((String) cfg.get("alipayPublicKey"));
        }
        return builder.build();
    }
}
