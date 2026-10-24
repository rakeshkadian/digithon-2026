package com.copilot.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface Repositories {

    interface ScanJobRepo extends JpaRepository<ScanJobEntity, String> {}

    interface FindingRepo extends JpaRepository<FindingEntity, Long> {
        List<FindingEntity> findByScanIdOrderByTableNameAscIdAsc(String scanId);
        List<FindingEntity> findByScanIdAndStatus(String scanId, String status);
    }

    interface PlanRepo extends JpaRepository<PlanEntity, String> {
        List<PlanEntity> findByScanIdOrderByCreatedAtDesc(String scanId);
    }

    interface AuditRepo extends JpaRepository<AuditEventEntity, Long> {
        List<AuditEventEntity> findTop200ByOrderByAtDesc();
    }
}
