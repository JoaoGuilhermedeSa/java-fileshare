package com.fileshare.service;

import com.fileshare.config.EncryptionProperties;
import com.fileshare.exception.EncryptionException;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class EncryptionService {

    private static final String ALGORITHM    = "AES/GCM/NoPadding";
    private static final int    GCM_TAG_BITS = 128;
    private static final int    IV_BYTES     = 12;
    private static final int    KEY_BITS     = 256;
    private static final int    PBKDF2_ITERS = 310_000;

    private final SecretKey secretKey;
    private final SecureRandom random = new SecureRandom();

    public EncryptionService(EncryptionProperties props) {
        this.secretKey = deriveKey(props.passphrase(), props.salt());
    }

    public byte[] encrypt(byte[] plaintext) {
        try {
            var iv = generateIv();
            var cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            var ciphertext = cipher.doFinal(plaintext);

            var result = new byte[IV_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, IV_BYTES);
            System.arraycopy(ciphertext, 0, result, IV_BYTES, ciphertext.length);
            return result;
        } catch (Exception e) {
            throw new EncryptionException("Encryption failed", e);
        }
    }

    public byte[] decrypt(byte[] packed) {
        try {
            var iv = new byte[IV_BYTES];
            System.arraycopy(packed, 0, iv, 0, IV_BYTES);

            var ciphertext = new byte[packed.length - IV_BYTES];
            System.arraycopy(packed, IV_BYTES, ciphertext, 0, ciphertext.length);

            var cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new EncryptionException("Decryption failed — file may be corrupted or key is wrong", e);
        }
    }

    public String extractIvHex(byte[] packed) {
        var iv = new byte[IV_BYTES];
        System.arraycopy(packed, 0, iv, 0, IV_BYTES);
        return HexFormat.of().formatHex(iv);
    }

    private byte[] generateIv() {
        var iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        return iv;
    }

    private static SecretKey deriveKey(String passphrase, String saltBase64) {
        try {
            var salt = Base64.getDecoder().decode(saltBase64);
            var factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERS, KEY_BITS);
            var tmp = factory.generateSecret(spec);
            return new SecretKeySpec(tmp.getEncoded(), "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive encryption key from passphrase", e);
        }
    }
}
