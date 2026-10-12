package com.legacyvault.repository;

import com.legacyvault.model.VaultAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VaultAssetRepository extends JpaRepository<VaultAsset, Long> {

    List<VaultAsset> findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(Long userId);

    Optional<VaultAsset> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndIsActiveTrue(Long userId);
}
