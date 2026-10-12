package com.legacyvault.service;

import com.legacyvault.model.User;
import com.legacyvault.model.VaultAsset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

/**
 * Service providing encrypted file storage at rest with AES-256-GCM,
 * strict path-traversal prevention, file extension whitelisting, and ownership controls.
 */
@Service
public class FileStorageService {

    private static final long MAX_FILE_SIZE = 15 * 1024 * 1024; // 15 Megabytes
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "txt", "jpg", "jpeg", "png"
    );

    private final Path rootStorageLocation;
    private final SecretKey fileEncryptionKey;
    private final SecureRandom secureRandom;

    public FileStorageService(
            @Value("${vault.storage.upload-dir:./storage/vault_files}") String uploadDir,
            @Value("${vault.encryption.secret}") String secret) {
        this.rootStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.secureRandom = new SecureRandom();

        try {
            Files.createDirectories(this.rootStorageLocation);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            this.fileEncryptionKey = new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize encrypted file storage", e);
        }
    }

    public record StoredFileInfo(String originalFilename, String contentType, long sizeBytes, String storagePath) {}

    /**
     * Validates and encrypts a document file, writing the ciphertext to private disk storage.
     */
    public StoredFileInfo storeFile(User user, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot store an empty file.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds the 15MB limit.");
        }

        String originalFilename = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "document"
        );

        // Path traversal defense
        if (originalFilename.contains("..") || originalFilename.contains("/") || originalFilename.contains("\\")) {
            throw new IllegalArgumentException("Invalid filename characters detected.");
        }

        String extension = getFileExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "Unsupported file type '." + extension + "'. Allowed formats: PDF, DOC, DOCX, TXT, JPG, PNG."
            );
        }

        // Generate isolated storage path per user
        String storageFileName = "user_" + user.getId() + "_" + UUID.randomUUID() + ".enc";
        Path userDir = this.rootStorageLocation.resolve("user_" + user.getId()).normalize();
        if (!userDir.startsWith(this.rootStorageLocation)) {
            throw new SecurityException("Path traversal attempt detected.");
        }
        Files.createDirectories(userDir);

        Path targetPath = userDir.resolve(storageFileName).normalize();
        if (!targetPath.startsWith(userDir)) {
            throw new SecurityException("Path traversal attempt detected.");
        }

        // Encrypt file bytes with AES-256-GCM
        byte[] iv = new byte[IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, fileEncryptionKey, spec);

            byte[] cipherBytes = cipher.doFinal(file.getBytes());

            // Write: [12 bytes IV][Ciphertext]
            byte[] outputBytes = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, outputBytes, 0, iv.length);
            System.arraycopy(cipherBytes, 0, outputBytes, iv.length, cipherBytes.length);

            Files.write(targetPath, outputBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            throw new IOException("Failed to encrypt document bytes", e);
        }

        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        return new StoredFileInfo(originalFilename, contentType, file.getSize(), targetPath.toString());
    }

    /**
     * Reads and decrypts an encrypted document for its verified owner.
     */
    public byte[] loadAndDecryptFile(User user, VaultAsset asset) throws IOException {
        if (!asset.getUser().getId().equals(user.getId())) {
            throw new SecurityException("Unauthorized access attempt to vault asset file.");
        }

        return loadAndDecryptDirectly(asset.getStoragePath());
    }

    /**
     * Stores and encrypts a supporting evidence document (e.g. death certificate) privately on disk.
     */
    public StoredFileInfo storeEvidenceFile(Long claimReferenceId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot store an empty evidence file.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Evidence file size exceeds 15MB limit.");
        }

        String originalFilename = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "evidence_document"
        );

        if (originalFilename.contains("..") || originalFilename.contains("/") || originalFilename.contains("\\")) {
            throw new IllegalArgumentException("Invalid filename characters detected in evidence document.");
        }

        String extension = getFileExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "Unsupported evidence file type '." + extension + "'. Allowed formats: PDF, DOC, DOCX, TXT, JPG, PNG."
            );
        }

        Path evidenceDir = this.rootStorageLocation.resolve("evidence").normalize();
        if (!evidenceDir.startsWith(this.rootStorageLocation)) {
            throw new SecurityException("Path traversal attempt detected.");
        }
        Files.createDirectories(evidenceDir);

        String storageFileName = "claim_" + claimReferenceId + "_" + UUID.randomUUID() + ".enc";
        Path targetPath = evidenceDir.resolve(storageFileName).normalize();
        if (!targetPath.startsWith(evidenceDir)) {
            throw new SecurityException("Path traversal attempt detected.");
        }

        byte[] iv = new byte[IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, fileEncryptionKey, spec);

            byte[] cipherBytes = cipher.doFinal(file.getBytes());

            byte[] outputBytes = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, outputBytes, 0, iv.length);
            System.arraycopy(cipherBytes, 0, outputBytes, iv.length, cipherBytes.length);

            Files.write(targetPath, outputBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            throw new IOException("Failed to encrypt evidence document bytes", e);
        }

        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        return new StoredFileInfo(originalFilename, contentType, file.getSize(), targetPath.toString());
    }

    /**
     * Reads and decrypts an encrypted file path directly after authorization checks have succeeded.
     */
    public byte[] loadAndDecryptDirectly(String storagePath) throws IOException {
        if (storagePath == null) {
            throw new FileNotFoundException("No attached file storage path provided.");
        }

        Path filePath = Paths.get(storagePath).toAbsolutePath().normalize();
        if (!Files.exists(filePath) || !filePath.startsWith(this.rootStorageLocation)) {
            throw new FileNotFoundException("Encrypted document file does not exist on disk.");
        }

        byte[] allBytes = Files.readAllBytes(filePath);
        if (allBytes.length < IV_LENGTH_BYTES) {
            throw new IOException("Corrupted encrypted file structure.");
        }

        byte[] iv = Arrays.copyOfRange(allBytes, 0, IV_LENGTH_BYTES);
        byte[] cipherBytes = Arrays.copyOfRange(allBytes, IV_LENGTH_BYTES, allBytes.length);

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, fileEncryptionKey, spec);

            return cipher.doFinal(cipherBytes);
        } catch (Exception e) {
            throw new IOException("Cryptographic decryption failed for file", e);
        }
    }

    /**
     * Deletes the encrypted file from disk.
     */
    public void deleteFile(String storagePath) {
        if (storagePath == null) return;
        try {
            Path filePath = Paths.get(storagePath).toAbsolutePath().normalize();
            if (filePath.startsWith(this.rootStorageLocation)) {
                Files.deleteIfExists(filePath);
            }
        } catch (Exception ignored) {
        }
    }

    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex == -1) ? "" : filename.substring(dotIndex + 1);
    }

    public static class FileNotFoundException extends IOException {
        public FileNotFoundException(String message) {
            super(message);
        }
    }
}
