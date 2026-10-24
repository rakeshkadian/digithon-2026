package com.copilot.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final Repositories.AuditRepo repo;

    public void log(String action, String entityRef, String detail) {
        AuditEventEntity e = new AuditEventEntity();
        e.setActor("demo-user");
        e.setAction(action);
        e.setEntityRef(entityRef);
        e.setDetail(detail);
        repo.save(e);
    }
}
