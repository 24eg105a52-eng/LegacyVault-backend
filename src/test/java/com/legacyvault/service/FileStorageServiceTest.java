package com.legacyvault.service;

import com.legacyvault.model.User;
import com.legacyvault.model.VaultAsset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService fileStorageService;
    private User owner;
    private User unauthorizedUser;

    @BeforeEach
    void setUp() {
        String testSecret = "TestSecretKeyForAes256GcmFileEncryption2026";
        fileStorageService = new FileStorageService(tempDir.toString(), testSecret);

        owner = new User();
        owner.setId(10L);
        owner.setEmail("owner@example.com");

        unauthorizedUser = new User();
        unauthorizedUser.setId(20L);
        unauthorizedUser.setEmail("other@example.com");
    }

    @Test
    void storeFile_AndLoadAndDecryptFile_ShouldSuccessfullyEncryptAndDecrypt() throws IOException {
        byte[] originalContent = "Confidential Last Will & Testament Content 2026".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "last_will.pdf", "application/pdf", originalContent
        );

        FileStorageService.StoredFileInfo storedInfo = fileStorageService.storeFile(owner, file);

        assertNotNull(storedInfo);
        assertEquals("last_will.pdf", storedInfo.originalFilename());
        assertEquals("application/pdf", storedInfo.contentType());
        assertEquals(originalContent.length, storedInfo.sizeBytes());

        // File on disk must NOT equal plaintext
        byte[] diskBytes = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(storedInfo.storagePath()));
        assertFalse(java.util.Arrays.equals(originalContent, diskBytes), "Disk file should be encrypted, not plaintext");

        // Verify decryption for owner
        VaultAsset asset = new VaultAsset();
        asset.setUser(owner);
        asset.setStoragePath(storedInfo.storagePath());

        byte[] decrypted = fileStorageService.loadAndDecryptFile(owner, asset);
        assertArrayEquals(originalContent, decrypted);
    }

    @Test
    void loadAndDecryptFile_WhenUnauthorizedUserAttemptsAccess_ShouldThrowSecurityException() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "deed.png", "image/png", "PNG IMAGE BYTES".getBytes()
        );
        FileStorageService.StoredFileInfo storedInfo = fileStorageService.storeFile(owner, file);

        VaultAsset asset = new VaultAsset();
        asset.setUser(owner);
        asset.setStoragePath(storedInfo.storagePath());

        assertThrows(SecurityException.class, () -> {
            fileStorageService.loadAndDecryptFile(unauthorizedUser, asset);
        });
    }

    @Test
    void storeFile_WhenInvalidExtension_ShouldRejectWithIllegalArgumentException() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "malicious_script.exe", "application/x-msdownload", "MALICIOUS".getBytes()
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            fileStorageService.storeFile(owner, file);
        });

        assertTrue(ex.getMessage().contains("Unsupported file type"));
    }

    @Test
    void storeFile_WhenPathTraversalInFilename_ShouldReject() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../etc/passwd.txt", "text/plain", "ATTACK".getBytes()
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            fileStorageService.storeFile(owner, file);
        });

        assertTrue(ex.getMessage().contains("Invalid filename"));
    }

    @Test
    void storeFile_WhenFileExceedsLimit_ShouldReject() {
        byte[] largeBytes = new byte[16 * 1024 * 1024]; // 16MB > 15MB limit
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.pdf", "application/pdf", largeBytes
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            fileStorageService.storeFile(owner, file);
        });

        assertTrue(ex.getMessage().contains("15MB limit"));
    }
}
