package com.legacyvault.repository;

import com.legacyvault.model.DeathClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeathClaimRepository extends JpaRepository<DeathClaim, Long> {

    List<DeathClaim> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<DeathClaim> findByClaimantEmailOrderByCreatedAtDesc(String claimantEmail);

    List<DeathClaim> findByClaimStatusOrderByCreatedAtDesc(String claimStatus);

    Optional<DeathClaim> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndClaimStatusIn(Long userId, List<String> statuses);

    List<DeathClaim> findByContactId(Long contactId);
}
