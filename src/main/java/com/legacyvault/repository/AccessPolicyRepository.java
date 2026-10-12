package com.legacyvault.repository;

import com.legacyvault.model.AccessPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccessPolicyRepository extends JpaRepository<AccessPolicy, Long> {

    List<AccessPolicy> findByAssetUserId(Long userId);

    List<AccessPolicy> findByContactId(Long contactId);

    Optional<AccessPolicy> findByAssetIdAndContactId(Long assetId, Long contactId);

    Optional<AccessPolicy> findByIdAndAssetUserId(Long id, Long userId);

    List<AccessPolicy> findByAssetUserIdAndIsAuthorizedTrue(Long userId);

    boolean existsByAssetIdAndContactId(Long assetId, Long contactId);
}
