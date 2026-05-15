package com.aipay.common.util;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

class IdGeneratorTest {

    @Test
    void chargeId_hasCh_prefix() {
        assertThat(IdGenerator.chargeId()).startsWith("ch_");
    }

    @Test
    void refundId_hasRe_prefix() {
        assertThat(IdGenerator.refundId()).startsWith("re_");
    }

    @Test
    void chargeId_isUnique() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            ids.add(IdGenerator.chargeId());
        }
        assertThat(ids).hasSize(1000);
    }

    @Test
    void liveApiKey_hasSk_live_prefix_and_32CharSuffix() {
        String key = IdGenerator.liveApiKey();
        assertThat(key).startsWith("sk_live_");
        assertThat(key.substring("sk_live_".length())).hasSize(32);
    }

    @Test
    void testApiKey_hasSk_test_prefix() {
        assertThat(IdGenerator.testApiKey()).startsWith("sk_test_");
    }
}
