package com.fileshare;

import com.fileshare.config.EncryptionProperties;
import com.fileshare.exception.EncryptionException;
import com.fileshare.service.EncryptionService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionServiceTest {

    private final EncryptionService svc = new EncryptionService(
            new EncryptionProperties("test-passphrase-do-not-use-in-prod", "dGhpcyBpcyBhIHNhbHQ=")
    );

    // ── Legacy byte-array tests ───────────────────────────────────────────────

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
        encrypted[encrypted.length - 1] ^= 0xFF;
        assertThrows(EncryptionException.class, () -> svc.decrypt(encrypted));
    }

    @Test
    void sameInputProducesDifferentCiphertexts() {
        var data = "same data".getBytes();
        assertFalse(java.util.Arrays.equals(svc.encrypt(data), svc.encrypt(data)));
    }

    // ── Streaming chunked-AEAD tests ──────────────────────────────────────────

    @Test
    void encryptStreamThenDecryptStreamReturnsOriginal() throws IOException {
        var original  = "Hello, streaming FileShare!".getBytes();
        var decrypted = streamRoundTrip(original);
        assertArrayEquals(original, decrypted);
    }

    @Test
    void encryptStreamHandlesEmptyInput() throws IOException {
        var decrypted = streamRoundTrip(new byte[0]);
        assertArrayEquals(new byte[0], decrypted);
    }

    @Test
    void encryptStreamHandlesDataSpanningMultipleChunks() throws IOException {
        // 2 MiB + 7 bytes forces two full 1 MiB chunks plus one partial chunk
        var original = new byte[2 * EncryptionService.CHUNK_SIZE + 7];
        Arrays.fill(original, (byte) 0xAB);
        var decrypted = streamRoundTrip(original);
        assertArrayEquals(original, decrypted);
    }

    @Test
    void encryptStreamProducesDifferentCiphertextsForSameInput() throws IOException {
        var data = "same data".getBytes();
        var enc1 = encryptToBytes(data);
        var enc2 = encryptToBytes(data);
        assertFalse(Arrays.equals(enc1, enc2));
    }

    @Test
    void decryptStreamThrowsOnTamperedChunk() throws IOException {
        var encrypted = encryptToBytes("tamper test data".getBytes());
        // Byte 21 = first byte of ciphertext (after 1B version + 4B chunk_size + 4B encLen + 12B iv)
        encrypted[21] ^= 0xFF;
        assertThrows(EncryptionException.class, () -> decryptFromBytes(encrypted));
    }

    @Test
    void decryptStreamThrowsOnUnknownVersion() {
        // Construct a fake header with version = 99
        var fake = new byte[]{99, 0, 0x10, 0, 0};
        assertThrows(EncryptionException.class, () -> decryptFromBytes(fake));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private byte[] encryptToBytes(byte[] plaintext) throws IOException {
        var out = new ByteArrayOutputStream();
        svc.encryptStream(new ByteArrayInputStream(plaintext), out);
        return out.toByteArray();
    }

    private byte[] decryptFromBytes(byte[] encrypted) throws IOException {
        var out = new ByteArrayOutputStream();
        svc.decryptStream(new ByteArrayInputStream(encrypted), out);
        return out.toByteArray();
    }

    private byte[] streamRoundTrip(byte[] plaintext) throws IOException {
        return decryptFromBytes(encryptToBytes(plaintext));
    }
}
