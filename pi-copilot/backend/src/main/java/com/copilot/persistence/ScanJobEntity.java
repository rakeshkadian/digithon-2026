package com.copilot.persistence;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "scan_job")
public class ScanJobEntity {
    @Id
    private String id;
    private String repoUrl;
    private String branch;
    private String commitId;
    private String status;
    @Column(length = 2000)
    private String message;
    private Instant startedAt;
    private Instant finishedAt;
    @Column(columnDefinition = "text")
    private String snapshotJson;
}
