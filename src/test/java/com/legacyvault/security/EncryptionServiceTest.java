package com.legacyvault.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionServiceTest {

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService("SampleTestMasterSecretAES256KeyRequirementString2026");
    }

    @Test
    void encryptAndDecrypt_ShouldSuccessfullyRecoverOriginalPlaintext() {
        String sensitiveData = "Family Will & Bitcoin Recovery Seed Phrase 12 Words";

        EncryptionService.EncryptedPayload payload = encryptionService.encrypt(sensitiveData);

        assertNotNull(payload);
        assertNotNull(payload.cipherText());
        assertNotNull(payload.iv());
        assertNotEquals(sensitiveData, payload.cipherText());

        String decrypted = encryptionService.decrypt(payload.cipherText(), payload.iv());
        assertEquals(sensitiveData, decrypted);
    }

    @Test
    void encrypt_WithIdenticalPlaintext_ShouldProduceDifferentCiphertextsDueToFreshIvs() {
        String sensitiveData = "ConstantSensitiveInstruction";

        EncryptionService.EncryptedPayload first = encryptionService.encrypt(sensitiveData);
        EncryptionService.EncryptedPayload second = encryptionService.encrypt(sensitiveData);

        // AES-GCM must use random IVs each time to prevent replay/leakage
        assertNotEquals(first.iv(), second.iv());
        assertNotEquals(first.cipherText(), second.cipherText());
    }

    @Test
    void decrypt_WithTamperedCiphertext_ShouldThrowException() {
        String sensitiveData = "AuthenticData";
        EncryptionService.EncryptedPayload payload = encryptionService.encrypt(sensitiveData);

        // Tamper with ciphertext by altering a base64 character
        String tamperedCipher = payload.cipherText().substring(0, payload.cipherText().length() - 2) + "AB";

        assertThrows(RuntimeException.class, () ->
                encryptionService.decrypt(tamperedCipher, payload.iv())
        );
    }
}
