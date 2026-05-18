package com.aipay.core.service;

import com.aipay.channel.api.ChannelRouter;
import com.aipay.channel.api.model.NotifyRequest;
import com.aipay.channel.api.model.NotifyResult;
import com.aipay.common.enums.ChargeStatus;
import com.aipay.common.exception.BizException;
import com.aipay.common.exception.ErrorCode;
import com.aipay.core.domain.Charge;
import com.aipay.core.domain.NotifyRecord;
import com.aipay.core.mapper.ChargeMapper;
import com.aipay.core.mapper.NotifyRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyService {

    private static final String NOTIFY_KEY_PREFIX = "notify:";
    private static final long NOTIFY_TTL_SECONDS = 300;

    private final NotifyRecordMapper notifyRecordMapper;
    private final ChargeMapper chargeMapper;
    private final ChannelRouter channelRouter;
    private final ChannelConfigService channelConfigService;
    private final RedissonClient redissonClient;

    @Transactional
    public void processNotify(String channel, String transactionNo, String rawBody,
                               Map<String, String> headers, Map<String, String[]> params,
                               String mchId, String apiV3Key, String serialNo,
                               String privateKey, String alipayPublicKey) {
        String redisKey = NOTIFY_KEY_PREFIX + channel + ":" + transactionNo;
        RBucket<String> bucket = redissonClient.getBucket(redisKey);

        boolean acquired = bucket.setIfAbsent("processing", Duration.ofSeconds(NOTIFY_TTL_SECONDS));
        if (!acquired) {
            log.info("Duplicate notification ignored: {}", redisKey);
            return;
        }

        NotifyRecord record = new NotifyRecord();
        record.setNotifyKey(channel + ":" + transactionNo);
        record.setChannel(channel);
        record.setRawBody(rawBody);
        record.setStatus("processing");
        record.setProcessCount(1);
        notifyRecordMapper.insert(record);

        try {
            NotifyRequest request = NotifyRequest.builder()
                .channel(channel)
                .rawBody(rawBody)
                .headers(headers)
                .params(params)
                .mchId(mchId)
                .apiV3Key(apiV3Key)
                .serialNo(serialNo)
                .privateKey(privateKey)
                .alipayPublicKey(alipayPublicKey)
                .build();

            NotifyResult result = channelRouter.route(channel).parseNotify(request);

            if (!result.isSuccess()) {
                throw new BizException(ErrorCode.NOTIFY_SIGNATURE_INVALID,
                    result.getFailureReason());
            }

            Charge charge = chargeMapper.selectOne(new LambdaQueryWrapper<Charge>()
                .eq(Charge::getOutTradeNo, result.getOutTradeNo()));

            if (charge == null) {
                log.warn("Charge not found for outTradeNo: {}", result.getOutTradeNo());
                return;
            }

            record.setChargeId(charge.getChargeId());

            if (charge.getAmount() != result.getPaidAmount()) {
                log.error("Amount mismatch: expected {} got {} for charge {}",
                    charge.getAmount(), result.getPaidAmount(), charge.getChargeId());
                throw new RuntimeException("Amount mismatch in payment notification");
            }

            if (ChargeStatus.pending.name().equals(charge.getStatus())) {
                charge.setStatus(ChargeStatus.paid.name());
                charge.setPaid(1);
                charge.setPaidAt(LocalDateTime.now());
                charge.setTransactionNo(result.getTransactionNo());
                chargeMapper.updateById(charge);
            }

            record.setStatus("success");
            notifyRecordMapper.updateById(record);

        } catch (Exception e) {
            log.error("Failed to process notification {}: {}", redisKey, e.getMessage(), e);
            record.setStatus("failed");
            notifyRecordMapper.updateById(record);
            bucket.delete();
            throw e;
        }
    }
}
