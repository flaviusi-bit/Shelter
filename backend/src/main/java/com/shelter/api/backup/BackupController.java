package com.shelter.api.backup;

import com.shelter.api.audit.AuditLogService;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/admin/backups")
public class BackupController {
    private final Path root;
    private final AuditLogService audit;

    public BackupController(@Value("${shelter.backup.directory:./backups}") String directory, AuditLogService audit) {
        this.root = Paths.get(directory).toAbsolutePath().normalize();
        this.audit = audit;
    }

    @GetMapping
    public List<BackupInfo> list() throws IOException {
        if (!Files.isDirectory(root)) return List.of();
        try (Stream<Path> stream = Files.list(root)) {
            return stream.filter(Files::isDirectory)
                .map(this::describe)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(BackupInfo::createdAt).reversed())
                .collect(Collectors.toList());
        }
    }

    @PostMapping("/{name}/verify")
    public BackupVerification verify(@PathVariable String name, Authentication auth) throws IOException {
        Path dir = safeDirectory(name);
        if (!Files.isDirectory(dir)) throw new IllegalArgumentException("Backup not found");
        Path sums = dir.resolve("SHA256SUMS");
        if (!Files.isRegularFile(sums)) {
            audit.record(auth.getName(), "VERIFY_BACKUP", "BACKUP", null, "name=" + name + ", valid=false, reason=SHA256SUMS missing");
            return new BackupVerification(false, "SHA256SUMS is missing");
        }
        List<String> failures = new ArrayList<>();
        for (String line : Files.readAllLines(sums, StandardCharsets.UTF_8)) {
            String[] parts = line.trim().split("\\s+", 2);
            if (parts.length != 2) continue;
            Path file = dir.resolve(parts[1]).normalize();
            if (!file.startsWith(dir) || !Files.isRegularFile(file)) {
                failures.add(parts[1] + ": missing");
                continue;
            }
            String actual = sha256(file);
            if (!actual.equalsIgnoreCase(parts[0])) failures.add(parts[1] + ": checksum mismatch");
        }
        boolean valid = failures.isEmpty();
        audit.record(auth.getName(), "VERIFY_BACKUP", "BACKUP", null, "name=" + name + ", valid=" + valid);
        return valid
            ? new BackupVerification(true, "Backup integrity verified")
            : new BackupVerification(false, String.join("; ", failures));
    }

    private BackupInfo describe(Path dir) {
        try {
            Path db = dir.resolve("database.dump");
            Path docs = dir.resolve("documents.tar.gz");
            Path sums = dir.resolve("SHA256SUMS");
            if (!Files.isRegularFile(db) || !Files.isRegularFile(docs)) return null;
            Instant created = Files.getLastModifiedTime(dir).toInstant();
            return new BackupInfo(dir.getFileName().toString(), created,
                Files.size(db), Files.size(docs), Files.isRegularFile(sums));
        } catch (IOException e) {
            return null;
        }
    }

    private Path safeDirectory(String name) {
        Path dir = root.resolve(name).normalize();
        if (!dir.startsWith(root) || !dir.getFileName().toString().equals(name))
            throw new IllegalArgumentException("Invalid backup name");
        return dir;
    }

    private String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var in = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record BackupInfo(String name, Instant createdAt, long databaseBytes, long documentsBytes, boolean checksumAvailable) {}
    public record BackupVerification(boolean valid, String message) {}
}
