package com.shelter.api.audit;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import java.util.List;
import org.springframework.data.domain.PageRequest;

@RestController
@RequestMapping("/api/admin/audit")
public class AuditLogController {
    private final AuditLogRepository repository;
    public AuditLogController(AuditLogRepository repository) { this.repository = repository; }

    @GetMapping
    public ResponseEntity<List<AuditLog>> list(@RequestParam(defaultValue = "200") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(repository.findByOrderByOccurredAtDesc(PageRequest.of(0, safeLimit)));
    }
}
