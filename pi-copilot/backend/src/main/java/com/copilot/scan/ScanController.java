package com.copilot.scan;

import com.copilot.persistence.FindingEntity;
import com.copilot.persistence.ScanJobEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class ScanController {

    private final ScanService scanService;

    public record ReviewRequest(String status, String comment) {}

    @PostMapping("/scans")
    public ResponseEntity<Map<String, String>> start(@Valid @RequestBody ScanRequest req) {
        ScanJobEntity job = scanService.start(req);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("scanId", job.getId(), "status", job.getStatus()));
    }

    @GetMapping("/scans/{id}")
    public ResponseEntity<ScanJobEntity> get(@PathVariable String id) {
        return scanService.get(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/scans/{id}/findings")
    public List<FindingEntity> findings(@PathVariable String id) {
        return scanService.findings(id);
    }

    /** PATCH /api/findings/{id}  { "status": "APPROVED" | "REJECTED", "comment": "..." } */
    @PatchMapping("/findings/{id}")
    public ResponseEntity<FindingEntity> review(@PathVariable Long id, @RequestBody ReviewRequest req) {
        return scanService.review(id, req.status(), req.comment())
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
