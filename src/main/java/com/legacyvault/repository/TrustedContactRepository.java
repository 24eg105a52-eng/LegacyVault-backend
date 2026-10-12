package com.legacyvault.repository;

import com.legacyvault.model.TrustedContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrustedContactRepository extends JpaRepository<TrustedContact, Long> {

    List<TrustedContact> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<TrustedContact> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndEmail(Long userId, String email);

    long countByUserId(Long userId);
}
