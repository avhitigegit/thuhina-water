package lk.thuhina.water.common;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Encrypts secrets kept in the database (WhatsApp access token, app secret – design 9.2) with AES-256-GCM. The key is the
 * SHA-256 of {@code APP_ENCRYPTION_KEY} (environment only, never in Git). Stored as {@code v1:<base64 of iv + ciphertext>}.
 */
@Component
public class SecretCipher {

    public static final String KEY_MISSING = "ENCRYPTION_KEY_MISSING";
    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(@Value("${app.encryption-key:}") String encryptionKey) {
        this.key = encryptionKey == null || encryptionKey.isBlank() ? null : new SecretKeySpec(sha256(encryptionKey), "AES");
    }

    public boolean available() {
        return key != null;
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] out = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + out.length).put(iv).put(out).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /** The plain text, or null when there is no value or it cannot be read (other key). */
    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX) || key == null) {
            return null;
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(c.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return null;
        }
    }

    private SecretKeySpec requireKey() {
        if (key == null) {
            throw new BusinessException(KEY_MISSING, "Secrets cannot be saved: the server has no encryption key (APP_ENCRYPTION_KEY).");
        }
        return key;
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
