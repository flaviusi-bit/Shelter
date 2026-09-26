package com.shelter.api.audit;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/audit")
public class AuditLogController {
    private final AuditLogRepository repository;
    public AuditLogController(AuditLogRepository repository) { this.repository = repository; }

    @GetMapping
    public List<AuditLog> list(@RequestParam(defaultValue = "200") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return repository.findTop500ByOrderByOccurredAtDesc().stream().limit(safeLimit).toList();
    }
}
