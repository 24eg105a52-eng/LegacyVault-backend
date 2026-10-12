package com.legacyvault.service;

import com.legacyvault.model.AuditLog;
import com.legacyvault.model.User;
import com.legacyvault.model.VaultAsset;
import com.legacyvault.payload.VaultAssetRequest;
import com.legacyvault.payload.VaultAssetResponse;
import com.legacyvault.repository.AuditLogRepository;
import com.legacyvault.repository.VaultAssetRepository;
import com.legacyvault.security.EncryptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service managing encrypted digital vault assets with strict user ownership isolation.
 */
@Service
public class VaultService {

    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "DOCUMENT",
            "PERSONAL_INSTRUCTION",
            "FINANCIAL_DIRECTIVE",
            "SUBSCRIPTION_INVENTORY",
            "HARDWARE_RECOVERY_KEY"
    );

    private final VaultAssetRepository vaultAssetRepository;
    private final EncryptionService encryptionService;
    private final AuditLogRepository auditLogRepository;
    private final FileStorageService fileStorageService;

    @org.springframework.beans.factory.annotation.Autowired
    public VaultService(VaultAssetRepository vaultAssetRepository,
                        EncryptionService encryptionService,
                        AuditLogRepository auditLogRepository,
                        FileStorageService fileStorageService) {
        this.vaultAssetRepository = vaultAssetRepository;
        this.encryptionService = encryptionService;
        this.auditLogRepository = auditLogRepository;
        this.fileStorageService = fileStorageService;
    }

    /**
     * Lists assets belonging exclusively to the authenticated user.
     */
    @Transactional(readOnly = true)
    public List<VaultAssetResponse> getAssetsForUser(User user) {
        List<VaultAsset> assets = vaultAssetRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(user.getId());
        return assets.stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Retrieves a single asset with strict owner isolation.
     */
    @Transactional(readOnly = true)
    public Optional<VaultAssetResponse> getAssetById(User user, Long assetId) {
        return vaultAssetRepository.findByIdAndUserId(assetId, user.getId())
                .filter(VaultAsset::getIsActive)
                .map(this::mapToResponse);
    }

    /**
     * Encrypts and persists a new vault asset, with optional document file encryption.
     */
    @Transactional
    public VaultAssetResponse createAsset(User user, VaultAssetRequest request) {
        try {
            return createAssetWithFile(user, request, null);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create vault asset", e);
        }
    }

    /**
     * Encrypts and persists a new vault asset along with an optional uploaded document.
     */
    @Transactional
    public VaultAssetResponse createAssetWithFile(User user, VaultAssetRequest request, MultipartFile file) throws IOException {
        String category = sanitizeCategory(request.getCategory());

        VaultAsset asset = new VaultAsset();
        asset.setUser(user);
        asset.setTitle(request.getTitle().trim());
        asset.setCategory(category);
        asset.setDescription(request.getDescription());
        asset.setIsActive(true);

        if (request.getSecretContent() != null && !request.getSecretContent().trim().isEmpty()) {
            EncryptionService.EncryptedPayload encrypted = encryptionService.encrypt(request.getSecretContent());
            asset.setEncryptedContent(encrypted.cipherText());
            asset.setEncryptionIv(encrypted.iv());
        }

        if (file != null && !file.isEmpty()) {
            if (fileStorageService == null) {
                throw new IllegalStateException("File storage service is unavailable.");
            }
            FileStorageService.StoredFileInfo fileInfo = fileStorageService.storeFile(user, file);
            asset.setFileName(fileInfo.originalFilename());
            asset.setFileType(fileInfo.contentType());
            asset.setFileSizeBytes(fileInfo.sizeBytes());
            asset.setStoragePath(fileInfo.storagePath());
        }

        VaultAsset saved = vaultAssetRepository.save(asset);

        String auditDetails = "Created vault asset: " + saved.getTitle() + " (Category: " + saved.getCategory() +
                (saved.getFileName() != null ? ", Attached Document: " + saved.getFileName() : "") + ")";
        recordAuditLog(user, "ASSET_CREATED", String.valueOf(saved.getId()), auditDetails);

        return mapToResponse(saved);
    }

    /**
     * Updates an existing vault asset with strict owner validation.
     */
    @Transactional
    public Optional<VaultAssetResponse> updateAsset(User user, Long assetId, VaultAssetRequest request) {
        try {
            return updateAssetWithFile(user, assetId, request, null);
        } catch (IOException e) {
            throw new RuntimeException("Failed to update vault asset", e);
        }
    }

    /**
     * Updates an existing vault asset and optionally attaches/replaces an uploaded document.
     */
    @Transactional
    public Optional<VaultAssetResponse> updateAssetWithFile(User user, Long assetId, VaultAssetRequest request, MultipartFile file) throws IOException {
        Optional<VaultAsset> existingOpt = vaultAssetRepository.findByIdAndUserId(assetId, user.getId())
                .filter(VaultAsset::getIsActive);

        if (existingOpt.isEmpty()) {
            return Optional.empty();
        }

        VaultAsset asset = existingOpt.get();
        asset.setTitle(request.getTitle().trim());
        asset.setCategory(sanitizeCategory(request.getCategory()));
        asset.setDescription(request.getDescription());

        if (request.getSecretContent() != null && !request.getSecretContent().trim().isEmpty()) {
            EncryptionService.EncryptedPayload encrypted = encryptionService.encrypt(request.getSecretContent());
            asset.setEncryptedContent(encrypted.cipherText());
            asset.setEncryptionIv(encrypted.iv());
        }

        if (file != null && !file.isEmpty()) {
            if (fileStorageService != null) {
                if (asset.getStoragePath() != null) {
                    fileStorageService.deleteFile(asset.getStoragePath());
                }
                FileStorageService.StoredFileInfo fileInfo = fileStorageService.storeFile(user, file);
                asset.setFileName(fileInfo.originalFilename());
                asset.setFileType(fileInfo.contentType());
                asset.setFileSizeBytes(fileInfo.sizeBytes());
                asset.setStoragePath(fileInfo.storagePath());
            }
        }

        VaultAsset updated = vaultAssetRepository.save(asset);

        recordAuditLog(user, "ASSET_UPDATED", String.valueOf(updated.getId()),
                "Updated vault asset: " + updated.getTitle() +
                (updated.getFileName() != null ? ", Attached Document: " + updated.getFileName() : ""));

        return Optional.of(mapToResponse(updated));
    }

    public record DecryptedFile(String fileName, String contentType, long sizeBytes, byte[] content) {}

    /**
     * Securely retrieves and decrypts an attached document for its authenticated owner.
     */
    @Transactional(readOnly = true)
    public Optional<DecryptedFile> loadAssetFile(User user, Long assetId) throws IOException {
        Optional<VaultAsset> existingOpt = vaultAssetRepository.findByIdAndUserId(assetId, user.getId())
                .filter(VaultAsset::getIsActive);

        if (existingOpt.isEmpty()) {
            return Optional.empty();
        }

        VaultAsset asset = existingOpt.get();
        if (asset.getStoragePath() == null || fileStorageService == null) {
            return Optional.empty();
        }

        byte[] decryptedBytes = fileStorageService.loadAndDecryptFile(user, asset);
        recordAuditLog(user, "ASSET_DOCUMENT_DOWNLOADED", String.valueOf(asset.getId()),
                "Downloaded encrypted document: " + asset.getFileName());

        return Optional.of(new DecryptedFile(
                asset.getFileName(),
                asset.getFileType() != null ? asset.getFileType() : "application/octet-stream",
                asset.getFileSizeBytes() != null ? asset.getFileSizeBytes() : decryptedBytes.length,
                decryptedBytes
        ));
    }

    /**
     * Deactivates / removes a vault asset and deletes its encrypted disk storage.
     */
    @Transactional
    public boolean deleteAsset(User user, Long assetId) {
        Optional<VaultAsset> existingOpt = vaultAssetRepository.findByIdAndUserId(assetId, user.getId())
                .filter(VaultAsset::getIsActive);

        if (existingOpt.isEmpty()) {
            return false;
        }

        VaultAsset asset = existingOpt.get();
        asset.setIsActive(false);

        if (asset.getStoragePath() != null && fileStorageService != null) {
            fileStorageService.deleteFile(asset.getStoragePath());
        }

        vaultAssetRepository.save(asset);

        recordAuditLog(user, "ASSET_DELETED", String.valueOf(asset.getId()),
                "Deactivated vault asset: " + asset.getTitle());

        return true;
    }

    private VaultAssetResponse mapToResponse(VaultAsset asset) {
        String decryptedContent = null;
        boolean hasEncryption = asset.getEncryptedContent() != null && asset.getEncryptionIv() != null;

        if (hasEncryption) {
            try {
                decryptedContent = encryptionService.decrypt(asset.getEncryptedContent(), asset.getEncryptionIv());
            } catch (Exception e) {
                decryptedContent = "[Decryption unavailable]";
            }
        }

        return new VaultAssetResponse(
                asset.getId(),
                asset.getTitle(),
                asset.getCategory(),
                asset.getDescription(),
                decryptedContent,
                hasEncryption,
                asset.getFileName(),
                asset.getFileType(),
                asset.getFileSizeBytes(),
                asset.getCreatedAt(),
                asset.getUpdatedAt()
        );
    }

    private String sanitizeCategory(String category) {
        if (category == null || !ALLOWED_CATEGORIES.contains(category)) {
            return "DOCUMENT";
        }
        return category;
    }

    private void recordAuditLog(User user, String action, String resourceId, String details) {
        AuditLog auditLog = new AuditLog();
        auditLog.setUser(user);
        auditLog.setActorType("VAULT_OWNER");
        auditLog.setActorIdentifier(user.getEmail());
        auditLog.setAction(action);
        auditLog.setResourceType("VAULT_ASSET");
        auditLog.setResourceId(resourceId);
        auditLog.setDetails(details);
        auditLogRepository.save(auditLog);
    }
}
