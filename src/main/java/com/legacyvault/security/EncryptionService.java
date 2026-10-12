package com.legacyvault.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Enterprise AES-256-GCM encryption service for securing sensitive vault payloads.
 * Generates fresh cryptographic IVs per encryption operation and enforces authenticated data integrity.
 */
@Service
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;

    public EncryptionService(@Value("${vault.encryption.secret}") String secret) {
        try {
            // Derive a consistent 256-bit AES key from the master secret via SHA-256
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
            this.secureRandom = new SecureRandom();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize AES-256-GCM key", e);
        }
    }

    /**
     * Container holding Base64-encoded encrypted ciphertext and initialization vector.
     */
    public record EncryptedPayload(String cipherText, String iv) {}

    /**
     * Encrypts plaintext string using AES-256-GCM with a fresh random IV.
     */
    public EncryptedPayload encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return new EncryptedPayload(null, null);
        }

        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            String cipherTextBase64 = Base64.getEncoder().encodeToString(cipherBytes);
            String ivBase64 = Base64.getEncoder().encodeToString(iv);

            return new EncryptedPayload(cipherTextBase64, ivBase64);
        } catch (Exception e) {
            throw new RuntimeException("Encryption operation failed", e);
        }
    }

    /**
     * Decrypts an AES-256-GCM ciphertext using the provided IV.
     */
    public String decrypt(String cipherTextBase64, String ivBase64) {
        if (cipherTextBase64 == null || ivBase64 == null) {
            return null;
        }

        try {
            byte[] cipherBytes = Base64.getDecoder().decode(cipherTextBase64);
            byte[] iv = Base64.getDecoder().decode(ivBase64);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plainBytes = cipher.doFinal(cipherBytes);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption operation failed", e);
        }
    }
}
