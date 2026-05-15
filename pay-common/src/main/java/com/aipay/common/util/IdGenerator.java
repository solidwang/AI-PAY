package com.aipay.common.util;

import java.util.UUID;

public final class IdGenerator {

    private IdGenerator() {}

    /** "ch_" + 24 hex chars */
    public static String chargeId() {
        return "ch_" + uuid24();
    }

    /** "re_" + 24 hex chars */
    public static String refundId() {
        return "re_" + uuid24();
    }

    /** "MCH" + current millis */
    public static String merchantNo() {
        return "MCH" + System.currentTimeMillis();
    }

    /** "app_" + 16 hex chars */
    public static String appId() {
        return "app_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /** "sk_live_" + 32 hex chars (UUID without hyphens) */
    public static String liveApiKey() {
        return "sk_live_" + UUID.randomUUID().toString().replace("-", "");
    }

    /** "sk_test_" + 32 hex chars */
    public static String testApiKey() {
        return "sk_test_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String uuid24() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }
}
