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
    private static final int MAX_BACKUPS = 200;
    private static final int MAX_CHECKSUM_ENTRIES = 10_000;
    private static final int MAX_MANIFEST_LINE_LENGTH = 4_096;
    private static final java.util.regex.Pattern SHA256_LINE = java.util.regex.Pattern.compile("([0-9a-fA-F]{64})\\s+(.+)");

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
                .limit(MAX_BACKUPS)
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
        int entries = 0;
        try (Stream<String> lines = Files.lines(sums, StandardCharsets.UTF_8)) {
            var iterator = lines.iterator();
            while (iterator.hasNext()) {
                String line = iterator.next();
                if (line.length() > MAX_MANIFEST_LINE_LENGTH) {
                    failures.add("SHA256SUMS: manifest line too long");
                    break;
                }
                if (line.isBlank()) continue;
                if (++entries > MAX_CHECKSUM_ENTRIES) {
                    failures.add("SHA256SUMS: too many entries");
                    break;
                }
                var match = SHA256_LINE.matcher(line.trim());
                if (!match.matches()) {
                    failures.add("SHA256SUMS: invalid entry");
                    continue;
                }
                String expected = match.group(1);
                String relativeName = match.group(2).trim();
                Path file = dir.resolve(relativeName).normalize();
                if (!file.startsWith(dir) || !Files.isRegularFile(file)) {
                    failures.add(relativeName + ": missing");
                    continue;
                }
                String actual = sha256(file);
                if (!actual.equalsIgnoreCase(expected)) failures.add(relativeName + ": checksum mismatch");
            }
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
