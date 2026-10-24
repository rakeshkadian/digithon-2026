package com.copilot.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class AuditController {
    private final Repositories.AuditRepo repo;

    @GetMapping
    public List<AuditEventEntity> list() { return repo.findTop200ByOrderByAtDesc(); }
}
