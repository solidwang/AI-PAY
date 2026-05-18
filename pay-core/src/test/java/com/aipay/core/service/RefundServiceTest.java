package com.aipay.core.service;

import com.aipay.common.exception.BizException;
import com.aipay.core.domain.Charge;
import com.aipay.core.mapper.ChargeMapper;
import com.aipay.core.mapper.RefundMapper;
import com.aipay.channel.api.ChannelRouter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock RefundMapper refundMapper;
    @Mock ChargeMapper chargeMapper;
    @Mock ChannelRouter channelRouter;
    @Mock ChannelConfigService channelConfigService;
    @Mock RedissonClient redissonClient;
    @Mock RBucket<String> rBucket;
    @InjectMocks RefundService refundService;

    @Test
    void createRefund_chargeNotPaid_throwsBizException() {
        Charge charge = new Charge();
        charge.setChargeId("ch_abc");
        charge.setPaid(0);

        assertThatThrownBy(() ->
            refundService.createRefund(charge, 5000, "REFUND_001", "test refund")
        ).isInstanceOf(BizException.class)
         .hasMessageContaining("already paid");
    }

    @Test
    void createRefund_amountExceeded_throwsBizException() {
        Charge charge = new Charge();
        charge.setChargeId("ch_abc");
        charge.setPaid(1);
        charge.setAmount(9900);
        charge.setAmountRefunded(5000);

        assertThatThrownBy(() ->
            refundService.createRefund(charge, 5000, "REFUND_001", "test refund")
        ).isInstanceOf(BizException.class)
         .hasMessageContaining("exceeds");
    }
}
