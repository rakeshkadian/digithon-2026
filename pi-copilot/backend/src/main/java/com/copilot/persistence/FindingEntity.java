package com.copilot.persistence;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "finding")
public class FindingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String scanId;
    private String tableName;
    private String columnName;
    private String javaField;
    private String javaType;
    private String piType;
    private int confidence;
    @Column(length = 2000)
    private String reason;
    private String recommendation;
    private String status;          // NEW | APPROVED | REJECTED
    @Column(length = 1000)
    private String reviewComment;
    @Column(columnDefinition = "text")
    private String codeRefsJson;
}
