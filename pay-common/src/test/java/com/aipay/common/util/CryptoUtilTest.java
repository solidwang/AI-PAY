package com.aipay.common.util;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CryptoUtilTest {

    @Test
    void encryptAndDecrypt_roundTrip() {
        String plaintext = "{\"mchId\":\"1234567890\",\"apiV3Key\":\"secret\"}";
        String key = "my-32-char-encryption-key-here!!";

        String encrypted = CryptoUtil.encrypt(plaintext, key);
        String decrypted = CryptoUtil.decrypt(encrypted, key);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void encrypt_producesDifferentCiphertextEachTime() {
        String plaintext = "hello";
        String key = "test-key";

        String enc1 = CryptoUtil.encrypt(plaintext, key);
        String enc2 = CryptoUtil.encrypt(plaintext, key);

        assertThat(enc1).isNotEqualTo(enc2);
    }

    @Test
    void sha256_knownValue() {
        assertThat(CryptoUtil.sha256("hello"))
            .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }
}
