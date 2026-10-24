package com.copilot.persistence;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "remediation_plan")
public class PlanEntity {
    @Id
    private String id;
    private String scanId;
    private String status;         // GENERATED | PR_OPENED | BACKFILL_RUNNING | BACKFILL_DONE | VERIFIED | FAILED
    private String branch;
    @Column(length = 1000)
    private String prUrl;
    @Column(length = 2000)
    private String message;
    @Column(columnDefinition = "text")
    private String sqlText;
    @Column(columnDefinition = "text")
    private String filesJson;      // [{path, content, action: ADDED|MODIFIED}]
    private long rowsTotal;
    private long rowsDone;
    private long rowsVerified;
    private Instant createdAt = Instant.now();
}
