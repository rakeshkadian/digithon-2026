package com.copilot.remediation;

import com.copilot.persistence.PlanEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class PlanController {

    private final PlanService planService;

    /** POST /api/plans { scanId, token, openPullRequest } */
    @PostMapping
    public PlanEntity create(@RequestBody PlanService.PlanRequest req) throws Exception {
        return planService.create(req);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlanEntity> get(@PathVariable String id) {
        return planService.get(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(params = "scanId")
    public List<PlanEntity> byScan(@RequestParam String scanId) {
        return planService.byScan(scanId);
    }
}
