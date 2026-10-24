package com.copilot.scan;

import com.copilot.analyzer.RepoAnalyzer;
import com.copilot.git.GitCloneService;
import com.copilot.llm.LlmClassifier;
import com.copilot.model.Finding;
import com.copilot.model.RepoSnapshot;
import com.copilot.persistence.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Orchestrates clone -> analyze -> classify and persists every step. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScanService {

    private final Repositories.ScanJobRepo scanRepo;
    private final Repositories.FindingRepo findingRepo;
    private final ScanRunner runner;
    private final AuditService audit;

    public ScanJobEntity start(ScanRequest req) {
        ScanJobEntity job = new ScanJobEntity();
        job.setId(UUID.randomUUID().toString());
        job.setRepoUrl(req.repoUrl());
        job.setBranch(req.branch());
        job.setStatus("QUEUED");
        job.setStartedAt(Instant.now());
        scanRepo.save(job);
        audit.log("SCAN_STARTED", job.getId(), req.repoUrl());
        runner.run(job.getId(), req);
        return job;
    }

    public Optional<ScanJobEntity> get(String id) { return scanRepo.findById(id); }

    public List<FindingEntity> findings(String scanId) {
        return findingRepo.findByScanIdOrderByTableNameAscIdAsc(scanId);
    }

    public Optional<FindingEntity> review(Long findingId, String status, String comment) {
        return findingRepo.findById(findingId).map(f -> {
            f.setStatus(status);
            f.setReviewComment(comment);
            audit.log("FINDING_" + status, f.getTableName() + "." + f.getColumnName(), comment);
            return findingRepo.save(f);
        });
    }

    // ------------------------------------------------------------------ async worker

    @Slf4j
    @Service
    @RequiredArgsConstructor
    public static class ScanRunner {

        private final GitCloneService git;
        private final RepoAnalyzer analyzer;
        private final LlmClassifier classifier;
        private final Repositories.ScanJobRepo scanRepo;
        private final Repositories.FindingRepo findingRepo;
        private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

        @Async
        public void run(String jobId, ScanRequest req) {
            ScanJobEntity job = scanRepo.findById(jobId).orElseThrow();
            GitCloneService.CloneResult clone = null;
            try {
                update(job, "CLONING", null);
                clone = git.clone(req.repoUrl(), req.branch(), req.token());
                job.setCommitId(clone.commitId());
                job.setBranch(clone.branch());

                update(job, "ANALYZING", null);
                RepoSnapshot snap = analyzer.analyze(clone.dir(), req.repoUrl(), clone.branch(), clone.commitId());
                job.setSnapshotJson(mapper.writeValueAsString(snap));

                if (snap.getEntities().isEmpty()) {
                    update(job, "FAILED", "No @Entity classes found. Nothing to scan.");
                    return;
                }

                update(job, "CLASSIFYING", null);
                List<Finding> findings = classifier.classify(snap);
                for (Finding f : findings) {
                    FindingEntity e = new FindingEntity();
                    e.setScanId(jobId);
                    e.setTableName(f.getTable());
                    e.setColumnName(f.getColumn());
                    e.setJavaField(f.getJavaField());
                    e.setJavaType(snap.getEntities().stream()
                            .filter(en -> en.getTableName().equals(f.getTable())).findFirst()
                            .flatMap(en -> en.getFields().stream()
                                    .filter(fi -> fi.getColumnName().equals(f.getColumn())).findFirst())
                            .map(fi -> fi.getJavaType()).orElse("String"));
                    e.setPiType(f.getPiType());
                    e.setConfidence(f.getConfidence());
                    e.setReason(f.getReason());
                    e.setRecommendation(f.getRecommendation());
                    e.setStatus("NEW");
                    e.setCodeRefsJson(mapper.writeValueAsString(f.getCodeRefs()));
                    findingRepo.save(e);
                }
                update(job, "DONE", findings.size() + " columns classified");
            } catch (Exception e) {
                log.error("Scan {} failed", jobId, e);
                update(job, "FAILED", e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally {
                job.setFinishedAt(Instant.now());
                scanRepo.save(job);
                if (clone != null) git.cleanup(clone.dir());
            }
        }

        private void update(ScanJobEntity job, String status, String msg) {
            job.setStatus(status);
            if (msg != null) job.setMessage(msg);
            scanRepo.save(job);
        }
    }
}
