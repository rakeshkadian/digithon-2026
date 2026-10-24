package com.copilot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Everything the analyzer learned from the cloned repository.
 * Downstream steps (LLM, remediation) read only this object, never the file system.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepoSnapshot {

    private String repoUrl;
    private String branch;
    private String commitId;
    private String basePackage;
    private String javaVersion;

    /** Detected from application.yml / properties of the target app (password never stored). */
    private String datasourceUrl;
    private String datasourceUser;
    private boolean datasourcePasswordPresent;

    @Builder.Default private List<EntityInfo> entities = new ArrayList<>();
    @Builder.Default private List<ClassInfo> repositories = new ArrayList<>();
    @Builder.Default private List<ClassInfo> controllers = new ArrayList<>();
    @Builder.Default private List<ClassInfo> services = new ArrayList<>();
    @Builder.Default private List<ClassInfo> dtos = new ArrayList<>();

    /** key = "table.column", value = places in code that touch it */
    @Builder.Default private Map<String, List<CodeRef>> codeRefs = Map.of();

    /** Files that failed to parse; shown as a warning, never fails the job. */
    @Builder.Default private List<String> skippedFiles = new ArrayList<>();
}
