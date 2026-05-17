package com.fileshare;

import com.fileshare.config.EncryptionProperties;
import com.fileshare.service.EncryptionService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionServiceTest {

    private final EncryptionService svc = new EncryptionService(
            new EncryptionProperties("test-passphrase-do-not-use-in-prod", "dGhpcyBpcyBhIHNhbHQ=")
    );

    @Test
    void encryptThenDecryptReturnsOriginal() {
        var original  = "Hello, FileShare!".getBytes();
        var encrypted = svc.encrypt(original);
        var decrypted = svc.decrypt(encrypted);
        assertArrayEquals(original, decrypted);
    }

    @Test
    void encryptedBytesAreDifferentFromPlaintext() {
        var original  = "sensitive content".getBytes();
        var encrypted = svc.encrypt(original);
        assertFalse(java.util.Arrays.equals(original, encrypted));
    }

    @Test
    void tamperingCipherTextThrowsOnDecrypt() {
        var encrypted = svc.encrypt("data".getBytes());
        encrypted[encrypted.length - 1] ^= 0xFF; // flip last byte
        assertThrows(com.fileshare.exception.EncryptionException.class, () -> svc.decrypt(encrypted));
    }

    @Test
    void sameInputProducesDifferentCiphertexts() {
        var data = "same data".getBytes();
        assertFalse(java.util.Arrays.equals(svc.encrypt(data), svc.encrypt(data)));
    }
}
