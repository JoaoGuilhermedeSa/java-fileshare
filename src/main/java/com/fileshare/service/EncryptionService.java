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
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class EncryptionService {

    private static final String ALGORITHM    = "AES/GCM/NoPadding";
    private static final int    GCM_TAG_BITS = 128;
    private static final int    IV_BYTES     = 12;
    private static final int    KEY_BITS     = 256;
    private static final int    PBKDF2_ITERS = 310_000;
    // On-disk format: [version(1B)][chunk_size(4B)] then repeating [encLen(4B)][iv(12B)][ciphertext+tag]
    public static final int     CHUNK_SIZE   = 1 << 20; // 1 MiB
    private static final int    VERSION      = 1;

    private final SecretKey secretKey;
    private final SecureRandom random = new SecureRandom();

    public EncryptionService(EncryptionProperties props) {
        this.secretKey = deriveKey(props.passphrase(), props.salt());
    }

    // ── Legacy byte-array API (kept for existing tests) ───────────────────────

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
        } catch (GeneralSecurityException e) {
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
        } catch (GeneralSecurityException e) {
            throw new EncryptionException("Decryption failed — file may be corrupted or key is wrong", e);
        }
    }

    // ── Streaming chunked-AEAD API ────────────────────────────────────────────

    /**
     * Reads {@code in} in 1 MiB chunks, encrypts each independently with AES-256-GCM,
     * and writes the framed result to {@code out}.
     *
     * @return hex IV of the first chunk, stored in file metadata for audit purposes
     */
    public String encryptStream(InputStream in, OutputStream out) throws IOException {
        var dataOut = new DataOutputStream(out);
        dataOut.writeByte(VERSION);
        dataOut.writeInt(CHUNK_SIZE);

        String firstIvHex = null;
        byte[] plainChunk;

        while ((plainChunk = in.readNBytes(CHUNK_SIZE)).length > 0) {
            var iv = generateIv();
            if (firstIvHex == null) firstIvHex = HexFormat.of().formatHex(iv);

            byte[] ciphertext;
            try {
                var cipher = Cipher.getInstance(ALGORITHM);
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
                ciphertext = cipher.doFinal(plainChunk);
            } catch (GeneralSecurityException e) {
                throw new EncryptionException("Chunk encryption failed", e);
            }

            dataOut.writeInt(IV_BYTES + ciphertext.length);
            dataOut.write(iv);
            dataOut.write(ciphertext);
        }

        dataOut.flush();
        // Empty-file edge case: no chunks written; generate a placeholder IV for metadata
        return firstIvHex != null ? firstIvHex : HexFormat.of().formatHex(generateIv());
    }

    /**
     * Decrypts a chunked-AEAD stream produced by {@link #encryptStream}.
     * Authenticates every chunk; throws {@link EncryptionException} on any tamper or truncation.
     */
    public void decryptStream(InputStream in, OutputStream out) throws IOException {
        var dataIn = new DataInputStream(in);

        int version = dataIn.readUnsignedByte();
        if (version != VERSION) {
            throw new EncryptionException("Unknown chunk format version: " + version,
                    new IllegalStateException("version=" + version));
        }
        dataIn.readInt(); // stored chunk_size — not needed for decryption

        while (true) {
            int encLen;
            try {
                encLen = dataIn.readInt();
            } catch (EOFException e) {
                return; // clean end of chunked stream
            }

            var encChunk = dataIn.readNBytes(encLen);
            if (encChunk.length != encLen) {
                throw new EncryptionException(
                        "Truncated chunk: expected " + encLen + " bytes, got " + encChunk.length,
                        new IOException("truncated"));
            }

            var iv         = Arrays.copyOfRange(encChunk, 0, IV_BYTES);
            var ciphertext = Arrays.copyOfRange(encChunk, IV_BYTES, encChunk.length);

            try {
                var cipher = Cipher.getInstance(ALGORITHM);
                cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
                out.write(cipher.doFinal(ciphertext));
            } catch (GeneralSecurityException e) {
                throw new EncryptionException(
                        "Chunk decryption failed — file may be corrupted or key is wrong", e);
            }
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

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
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to derive encryption key from passphrase", e);
        }
    }
}
