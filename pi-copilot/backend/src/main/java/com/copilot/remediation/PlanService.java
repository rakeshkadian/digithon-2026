package com.copilot.remediation;

import com.copilot.git.GitCloneService;
import com.copilot.git.GitPublishService;
import com.copilot.model.RepoSnapshot;
import com.copilot.persistence.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/** Generates the remediation plan (SQL + Java diff), writes it to a branch and opens a PR. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanService {

    private final Repositories.ScanJobRepo scanRepo;
    private final Repositories.FindingRepo findingRepo;
    private final Repositories.PlanRepo planRepo;
    private final GitCloneService cloneService;
    private final GitPublishService publishService;
    private final CodeGenerator generator;
    private final AuditService audit;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public record PlanRequest(String scanId, String token, boolean openPullRequest) {}

    public PlanEntity create(PlanRequest req) throws Exception {
        ScanJobEntity scan = scanRepo.findById(req.scanId()).orElseThrow();
        List<FindingEntity> approved = findingRepo.findByScanIdAndStatus(req.scanId(), "APPROVED");
        if (approved.isEmpty()) throw new IllegalStateException("No approved findings. Approve at least one column first.");

        RepoSnapshot snap = mapper.readValue(scan.getSnapshotJson(), RepoSnapshot.class);

        PlanEntity plan = new PlanEntity();
        plan.setId(UUID.randomUUID().toString());
        plan.setScanId(scan.getId());
        plan.setStatus("GENERATING");
        plan.setBranch("pi-copilot/encrypt-" + approved.get(0).getTableName() + "-" + plan.getId().substring(0, 6));
        planRepo.save(plan);

        GitCloneService.CloneResult clone = cloneService.clone(scan.getRepoUrl(), scan.getBranch(), req.token());
        try {
            CodeGenerator.Result gen = generator.generate(clone.dir(), snap, approved);

            List<Map<String, String>> files = new ArrayList<>();
            for (CodeGenerator.GeneratedFile g : gen.getFiles()) {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("path", g.getPath());
                m.put("action", g.getAction());
                m.put("content", g.getContent());
                m.put("diff", "MODIFIED".equals(g.getAction()) ? unifiedDiff(g.getPath(), g.getOriginal(), g.getContent())
                                                               : unifiedDiff(g.getPath(), "", g.getContent()));
                files.add(m);
            }
            plan.setSqlText(gen.getSql());
            plan.setFilesJson(mapper.writeValueAsString(files));
            plan.setStatus("GENERATED");
            planRepo.save(plan);
            audit.log("PLAN_GENERATED", plan.getId(), approved.size() + " columns, " + files.size() + " files");

            if (req.openPullRequest()) {
                generator.writeToRepo(clone.dir(), gen);
                GitPublishService.PublishResult pub = publishService.publish(clone.dir(), scan.getRepoUrl(),
                        scan.getBranch(), req.token(), plan.getBranch(),
                        "PI Security Co-Pilot: encrypt " + approved.size() + " personal-data column(s)",
                        "Encrypt personal data columns (PI Security Co-Pilot)", gen.getPrBody());
                plan.setPrUrl(pub.prUrl());
                plan.setStatus("PR_OPENED");
                plan.setMessage(pub.prUrl() == null ? "Branch pushed: " + pub.branch() : "PR opened");
                planRepo.save(plan);
                audit.log("PR_OPENED", plan.getId(), String.valueOf(pub.prUrl()));
            }
        } catch (Exception e) {
            plan.setStatus("FAILED");
            plan.setMessage(e.getMessage());
            planRepo.save(plan);
            throw e;
        } finally {
            cloneService.cleanup(clone.dir());
        }
        return plan;
    }

    public Optional<PlanEntity> get(String id) { return planRepo.findById(id); }

    public List<PlanEntity> byScan(String scanId) { return planRepo.findByScanIdOrderByCreatedAtDesc(scanId); }

    private static String unifiedDiff(String path, String a, String b) {
        List<String> oldLines = a.isEmpty() ? List.of() : Arrays.asList(a.split("\n", -1));
        List<String> newLines = Arrays.asList(b.split("\n", -1));
        var patch = DiffUtils.diff(oldLines, newLines);
        return String.join("\n", UnifiedDiffUtils.generateUnifiedDiff("a/" + path, "b/" + path, oldLines, patch, 3));
    }
}
