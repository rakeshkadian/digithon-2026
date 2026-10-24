package com.copilot.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * AES-256-GCM + HMAC-SHA256 blind index.
 * Layout of a ciphertext: [1 byte key version][12 byte nonce][ciphertext + 16 byte tag].
 * The same algorithm is generated into the target application, so both sides agree.
 */
@Component
public class PiCrypto {

    public static final int KEY_VERSION = 1;
    private static final int NONCE_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec aesKey;
    private final SecretKeySpec macKey;
    private final SecureRandom random = new SecureRandom();

    public PiCrypto(@Value("${copilot.master-key}") String masterKeyBase64) {
        byte[] master = Base64.getDecoder().decode(masterKeyBase64);
        if (master.length < 32) throw new IllegalStateException("PI_MASTER_KEY must be at least 32 bytes (base64)");
        byte[] key = new byte[32];
        System.arraycopy(master, 0, key, 0, 32);
        this.aesKey = new SecretKeySpec(key, "AES");
        this.macKey = new SecretKeySpec(key, "HmacSHA256");   // MVP: same key; derive separately in prod
    }

    public byte[] encrypt(String plain) {
        if (plain == null) return null;
        try {
            byte[] nonce = new byte[NONCE_LEN];
            random.nextBytes(nonce);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[1 + NONCE_LEN + ct.length];
            out[0] = (byte) KEY_VERSION;
            System.arraycopy(nonce, 0, out, 1, NONCE_LEN);
            System.arraycopy(ct, 0, out, 1 + NONCE_LEN, ct.length);
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("encrypt failed", e);
        }
    }

    public String decrypt(byte[] blob) {
        if (blob == null) return null;
        try {
            byte[] nonce = new byte[NONCE_LEN];
            System.arraycopy(blob, 1, nonce, 0, NONCE_LEN);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] pt = c.doFinal(blob, 1 + NONCE_LEN, blob.length - 1 - NONCE_LEN);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("decrypt failed", e);
        }
    }

    /** Deterministic index for exact-match lookups. Lower-cased so email search is case-insensitive. */
    public String blindIndex(String plain) {
        if (plain == null) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(macKey);
            return HexFormat.of().formatHex(mac.doFinal(plain.trim().toLowerCase().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("hmac failed", e);
        }
    }
}
