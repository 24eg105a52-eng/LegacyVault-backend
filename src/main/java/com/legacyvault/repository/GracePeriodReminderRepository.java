package com.legacyvault.repository;

import com.legacyvault.model.GracePeriodReminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for GracePeriodReminder entity operations.
 */
@Repository
public interface GracePeriodReminderRepository extends JpaRepository<GracePeriodReminder, Long> {

    List<GracePeriodReminder> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByUserIdAndReminderNumberAndStatus(Long userId, Integer reminderNumber, String status);

    Optional<GracePeriodReminder> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    List<GracePeriodReminder> findByUserIdAndStatus(Long userId, String status);
}
