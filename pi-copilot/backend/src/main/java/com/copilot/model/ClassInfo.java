package com.copilot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** A repository, controller, service or DTO with the parts we care about. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassInfo {
    private String className;
    private String filePath;
    private String kind;                 // REPOSITORY | CONTROLLER | SERVICE | DTO
    private String managedEntity;        // for repositories: Employee from JpaRepository<Employee, Long>
    @Builder.Default private List<String> methodNames = new ArrayList<>();
    @Builder.Default private List<String> queries = new ArrayList<>();   // @Query strings
    @Builder.Default private List<String> fieldNames = new ArrayList<>(); // for DTOs
}
