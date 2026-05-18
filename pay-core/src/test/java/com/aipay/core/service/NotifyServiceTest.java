package com.aipay.core.service;

import com.aipay.channel.api.ChannelRouter;
import com.aipay.channel.api.PayChannel;
import com.aipay.channel.api.model.NotifyResult;
import com.aipay.core.domain.Charge;
import com.aipay.core.domain.NotifyRecord;
import com.aipay.core.mapper.ChargeMapper;
import com.aipay.core.mapper.NotifyRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotifyServiceTest {

    @Mock NotifyRecordMapper notifyRecordMapper;
    @Mock ChargeMapper chargeMapper;
    @Mock ChannelRouter channelRouter;
    @Mock ChannelConfigService channelConfigService;
    @Mock RedissonClient redissonClient;
    @Mock RBucket<String> rBucket;
    @InjectMocks NotifyService notifyService;

    @Test
    @SuppressWarnings("unchecked")
    void processNotify_alreadyProcessed_returnsImmediately() {
        when(redissonClient.getBucket(anyString())).thenReturn((RBucket) rBucket);
        when(rBucket.setIfAbsent(anyString(), any(Duration.class))).thenReturn(false);

        notifyService.processNotify("wechat_jsapi", "TX123", "body",
            Map.of(), null, "mch001", "key", "serial", "privKey", null);

        verify(chargeMapper, never()).selectOne(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void processNotify_newNotification_updatesChargeStatus() {
        when(redissonClient.getBucket(anyString())).thenReturn((RBucket) rBucket);
        when(rBucket.setIfAbsent(anyString(), any(Duration.class))).thenReturn(true);

        PayChannel channel = mock(PayChannel.class);
        when(channelRouter.route("wechat_jsapi")).thenReturn(channel);
        when(channel.parseNotify(any())).thenReturn(
            NotifyResult.builder()
                .success(true)
                .transactionNo("TX123")
                .outTradeNo("ORDER_001")
                .paidAmount(9900)
                .build()
        );

        Charge charge = new Charge();
        charge.setChargeId("ch_abc");
        charge.setStatus("pending");
        charge.setAmount(9900);
        when(chargeMapper.selectOne(any())).thenReturn(charge);
        when(chargeMapper.updateById(any(Charge.class))).thenReturn(1);
        when(notifyRecordMapper.insert(any(NotifyRecord.class))).thenReturn(1);
        when(notifyRecordMapper.updateById(any(NotifyRecord.class))).thenReturn(1);

        notifyService.processNotify("wechat_jsapi", "TX123", "body",
            Map.of(), null, "mch001", "key", "serial", "privKey", null);

        verify(chargeMapper).updateById(argThat((Charge c) -> "paid".equals(c.getStatus())));
    }
}
