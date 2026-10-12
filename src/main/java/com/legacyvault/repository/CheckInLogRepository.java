package com.legacyvault.repository;

import com.legacyvault.model.CheckInLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CheckInLogRepository extends JpaRepository<CheckInLog, Long> {

    List<CheckInLog> findByUserIdOrderByRecordedAtDesc(Long userId);
}
