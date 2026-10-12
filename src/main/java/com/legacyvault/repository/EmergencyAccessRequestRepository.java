package com.legacyvault.repository;

import com.legacyvault.model.EmergencyAccessRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmergencyAccessRequestRepository extends JpaRepository<EmergencyAccessRequest, Long> {

    List<EmergencyAccessRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndRequestStatus(Long userId, String requestStatus);
}
