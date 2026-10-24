package com.copilot.persistence;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "audit_event")
public class AuditEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Instant at = Instant.now();
    private String actor;
    private String action;
    private String entityRef;
    @Column(columnDefinition = "text")
    private String detail;
}
