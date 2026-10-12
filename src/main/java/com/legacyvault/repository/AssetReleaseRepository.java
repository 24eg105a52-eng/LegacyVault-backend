package com.legacyvault.repository;

import com.legacyvault.model.AssetRelease;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetReleaseRepository extends JpaRepository<AssetRelease, Long> {

    List<AssetRelease> findByContactEmailAndReleaseStatus(String contactEmail, String releaseStatus);

    List<AssetRelease> findByContactIdAndReleaseStatus(Long contactId, String releaseStatus);

    Optional<AssetRelease> findByAssetIdAndContactEmailAndReleaseStatus(Long assetId, String contactEmail, String releaseStatus);

    Optional<AssetRelease> findByAssetIdAndContactId(Long assetId, Long contactId);

    List<AssetRelease> findByClaimId(Long claimId);

    boolean existsByAssetIdAndContactId(Long assetId, Long contactId);
}
