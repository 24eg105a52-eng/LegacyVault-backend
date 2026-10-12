package com.legacyvault.service;

import com.legacyvault.model.AuditLog;
import com.legacyvault.model.User;
import com.legacyvault.model.VaultAsset;
import com.legacyvault.payload.VaultAssetRequest;
import com.legacyvault.payload.VaultAssetResponse;
import com.legacyvault.repository.AuditLogRepository;
import com.legacyvault.repository.VaultAssetRepository;
import com.legacyvault.security.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VaultServiceTest {

    @Mock
    private VaultAssetRepository vaultAssetRepository;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private VaultService vaultService;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        userA = new User();
        userA.setId(1L);
        userA.setEmail("usera@example.com");

        userB = new User();
        userB.setId(2L);
        userB.setEmail("userb@example.com");
    }

    @Test
    void getAssetsForUser_WhenNewAccount_ShouldReturnEmptyList() {
        when(vaultAssetRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(1L))
                .thenReturn(Collections.emptyList());

        List<VaultAssetResponse> assets = vaultService.getAssetsForUser(userA);

        assertNotNull(assets);
        assertTrue(assets.isEmpty());
    }

    @Test
    void createAsset_ShouldEncryptSensitivePayloadWithAes256Gcm() {
        VaultAssetRequest request = new VaultAssetRequest(
                "Recovery Seed Phrase",
                "HARDWARE_RECOVERY_KEY",
                "Hardware wallet seed words",
                "secret mnemonic phrase 12 words"
        );

        when(encryptionService.encrypt("secret mnemonic phrase 12 words"))
                .thenReturn(new EncryptionService.EncryptedPayload("base64EncryptedCipher", "base64Iv"));

        when(vaultAssetRepository.save(any(VaultAsset.class))).thenAnswer(invocation -> {
            VaultAsset a = invocation.getArgument(0);
            a.setId(10L);
            return a;
        });

        VaultAssetResponse created = vaultService.createAsset(userA, request);

        assertNotNull(created);
        assertEquals("Recovery Seed Phrase", created.getTitle());
        assertEquals("HARDWARE_RECOVERY_KEY", created.getCategory());

        ArgumentCaptor<VaultAsset> captor = ArgumentCaptor.forClass(VaultAsset.class);
        verify(vaultAssetRepository).save(captor.capture());
        VaultAsset saved = captor.getValue();
        assertEquals("base64EncryptedCipher", saved.getEncryptedContent());
        assertEquals("base64Iv", saved.getEncryptionIv());
        assertEquals(userA, saved.getUser());

        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void getAssetById_WhenAssetBelongsToDifferentUser_MustEnforceIsolationAndReturnEmpty() {
        // User B tries to view Asset #99 which belongs to User A
        when(vaultAssetRepository.findByIdAndUserId(99L, userB.getId()))
                .thenReturn(Optional.empty());

        Optional<VaultAssetResponse> result = vaultService.getAssetById(userB, 99L);

        assertTrue(result.isEmpty());
        verify(encryptionService, never()).decrypt(anyString(), anyString());
    }

    @Test
    void getAssetById_WhenOwnerAccesses_ShouldDecryptPayload() {
        VaultAsset asset = new VaultAsset();
        asset.setId(50L);
        asset.setUser(userA);
        asset.setTitle("Estate Deed");
        asset.setCategory("DOCUMENT");
        asset.setIsActive(true);
        asset.setEncryptedContent("cipherText");
        asset.setEncryptionIv("iv123");

        when(vaultAssetRepository.findByIdAndUserId(50L, 1L)).thenReturn(Optional.of(asset));
        when(encryptionService.decrypt("cipherText", "iv123")).thenReturn("Decrypted Deed Plaintext");

        Optional<VaultAssetResponse> responseOpt = vaultService.getAssetById(userA, 50L);

        assertTrue(responseOpt.isPresent());
        assertEquals("Decrypted Deed Plaintext", responseOpt.get().getSecretContent());
        assertTrue(responseOpt.get().isEncrypted());
    }

    @Test
    void deleteAsset_WhenDifferentUserAttemptsDelete_MustReject() {
        when(vaultAssetRepository.findByIdAndUserId(50L, userB.getId())).thenReturn(Optional.empty());

        boolean deleted = vaultService.deleteAsset(userB, 50L);

        assertFalse(deleted);
        verify(vaultAssetRepository, never()).delete(any(VaultAsset.class));
    }

    @Test
    void createAssetWithFile_ShouldEncryptAndStoreFile() throws IOException {
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.isEmpty()).thenReturn(false);

        FileStorageService.StoredFileInfo fileInfo = new FileStorageService.StoredFileInfo(
                "will.pdf", "application/pdf", 1024L, "storage/user_1/will.enc"
        );
        when(fileStorageService.storeFile(eq(userA), eq(mockFile))).thenReturn(fileInfo);

        VaultAssetRequest request = new VaultAssetRequest(
                "Last Will and Testament", "DOCUMENT", "Legal deed", null
        );

        when(vaultAssetRepository.save(any(VaultAsset.class))).thenAnswer(invocation -> {
            VaultAsset a = invocation.getArgument(0);
            a.setId(77L);
            return a;
        });

        VaultAssetResponse response = vaultService.createAssetWithFile(userA, request, mockFile);

        assertNotNull(response);
        assertEquals("Last Will and Testament", response.getTitle());
        assertEquals("will.pdf", response.getFileName());
        assertEquals("application/pdf", response.getFileType());
        assertTrue(response.isHasFile());

        verify(fileStorageService).storeFile(eq(userA), eq(mockFile));
        verify(vaultAssetRepository).save(any(VaultAsset.class));
    }

    @Test
    void loadAssetFile_WhenOwnerRequests_ShouldReturnDecryptedBytes() throws IOException {
        VaultAsset asset = new VaultAsset();
        asset.setId(88L);
        asset.setUser(userA);
        asset.setTitle("Family Trust");
        asset.setFileName("trust.pdf");
        asset.setFileType("application/pdf");
        asset.setFileSizeBytes(2048L);
        asset.setStoragePath("storage/user_1/trust.enc");
        asset.setIsActive(true);

        when(vaultAssetRepository.findByIdAndUserId(88L, userA.getId())).thenReturn(Optional.of(asset));
        byte[] expectedBytes = "Decrypted PDF Content".getBytes();
        when(fileStorageService.loadAndDecryptFile(userA, asset)).thenReturn(expectedBytes);

        Optional<VaultService.DecryptedFile> result = vaultService.loadAssetFile(userA, 88L);

        assertTrue(result.isPresent());
        assertEquals("trust.pdf", result.get().fileName());
        assertEquals("application/pdf", result.get().contentType());
        assertArrayEquals(expectedBytes, result.get().content());
    }

    @Test
    void deleteAsset_WhenHasFile_ShouldDeleteDiskFile() {
        VaultAsset asset = new VaultAsset();
        asset.setId(99L);
        asset.setUser(userA);
        asset.setIsActive(true);
        asset.setStoragePath("storage/user_1/file_to_delete.enc");

        when(vaultAssetRepository.findByIdAndUserId(99L, userA.getId())).thenReturn(Optional.of(asset));

        boolean deleted = vaultService.deleteAsset(userA, 99L);

        assertTrue(deleted);
        assertFalse(asset.getIsActive());
        verify(fileStorageService).deleteFile("storage/user_1/file_to_delete.enc");
        verify(vaultAssetRepository).save(asset);
    }
}
