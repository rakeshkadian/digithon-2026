package com.copilot.migration;

import com.copilot.persistence.PlanEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class BackfillController {

    private final BackfillService backfillService;

    /** POST /api/db/test { jdbcUrl, username, password } */
    @PostMapping("/db/test")
    public BackfillService.TestResult test(@RequestBody BackfillService.DbRequest db) {
        return backfillService.testConnection(db);
    }

    /** POST /api/plans/{id}/backfill { jdbcUrl, username, password, batchSize } */
    @PostMapping("/plans/{id}/backfill")
    public PlanEntity backfill(@PathVariable String id, @RequestBody BackfillService.DbRequest db) {
        return backfillService.start(id, db);
    }
}
