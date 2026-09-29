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
    private static final long MAX_TOTAL_VERIFY_BYTES = 1_073_741_824L;
    private static final int MAX_CHECKSUM_ENTRIES = 10_000;
    private static final int MAX_MANIFEST_LINE_LENGTH = 4_096;
    private static final int MAX_FAILURES = 100;
    private static final int MAX_BACKUP_NAME_LENGTH = 128;
    private static final int MAX_FAILURE_MESSAGE_LENGTH = 256;
    private static final java.util.regex.Pattern SHA256_LINE = java.util.regex.Pattern.compile("([0-9a-fA-F]{64})\\s+(.+)");
    private static final Set<String> EXPECTED_BACKUP_FILES = Set.of("database.dump", "documents.tar.gz");

    private final AuditLogService audit;

    public BackupController(@Value("${shelter.backup.directory:./backups}") String directory, AuditLogService audit) {
        this.root = Paths.get(directory).toAbsolutePath().normalize();
        this.audit = audit;
    }

    @GetMapping
    public List<BackupInfo> list() throws IOException {
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return List.of();
        try (Stream<Path> stream = Files.list(root)) {
            return stream.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                .limit(MAX_BACKUPS)
                .map(this::describe)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(BackupInfo::createdAt).reversed())
                .collect(Collectors.toList());
        }
    }

    @PostMapping("/{name}/verify")
    public BackupVerification verify(@PathVariable String name, Authentication auth) throws IOException {
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("Backup storage unavailable");
        Path dir = safeDirectory(name);
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("Backup not found");
        Path sums = dir.resolve("SHA256SUMS");
        if (!Files.isRegularFile(sums, LinkOption.NOFOLLOW_LINKS)) {
            audit.record(auth.getName(), "VERIFY_BACKUP", "BACKUP", null, "name=" + name + ", valid=false, reason=SHA256SUMS missing");
            return new BackupVerification(false, "SHA256SUMS is missing");
        }
        List<String> failures = new ArrayList<>();
        Set<String> verifiedFiles = new HashSet<>();
        long totalVerifiedBytes = 0L;
        int entries = 0;
        try (Stream<String> lines = Files.lines(sums, StandardCharsets.UTF_8)) {
            var iterator = lines.iterator();
            while (iterator.hasNext()) {
                String line = iterator.next();
                if (line.length() > MAX_MANIFEST_LINE_LENGTH) {
                    if (failures.size() < MAX_FAILURES) failures.add("SHA256SUMS: manifest line too long");
                    break;
                }
                if (line.isBlank()) continue;
                if (++entries > MAX_CHECKSUM_ENTRIES) {
                    if (failures.size() < MAX_FAILURES) failures.add("SHA256SUMS: too many entries");
                    break;
                }
                var match = SHA256_LINE.matcher(line.trim());
                if (!match.matches()) {
                    if (failures.size() < MAX_FAILURES) failures.add("SHA256SUMS: invalid entry");
                    continue;
                }
                String expected = match.group(1);
                String relativeName = match.group(2).trim();
                if (!EXPECTED_BACKUP_FILES.contains(relativeName) || !verifiedFiles.add(relativeName)) {
                    if (failures.size() < MAX_FAILURES) failures.add("SHA256SUMS: invalid or duplicate entry");
                    continue;
                }
                Path file = dir.resolve(relativeName).normalize();
                if (!file.startsWith(dir) || hasSymlinkComponent(dir, file.getParent()) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    if (failures.size() < MAX_FAILURES) failures.add(safeFailure(relativeName, "missing"));
                    continue;
                }
                long fileSize = Files.size(file);
                if (fileSize > MAX_TOTAL_VERIFY_BYTES - totalVerifiedBytes) {
                    if (failures.size() < MAX_FAILURES) failures.add(safeFailure(relativeName, "verification size limit exceeded"));
                    continue;
                }
                totalVerifiedBytes += fileSize;
                long sizeBeforeHash = fileSize;
                String actual = sha256(file);
                long sizeAfterHash = Files.size(file);
                if (sizeBeforeHash != sizeAfterHash) {
                    if (failures.size() < MAX_FAILURES) failures.add(safeFailure(relativeName, "file changed during verification"));
                    continue;
                }
                if (!actual.equalsIgnoreCase(expected) && failures.size() < MAX_FAILURES) failures.add(safeFailure(relativeName, "checksum mismatch"));
            }
        } catch (java.nio.charset.MalformedInputException e) {
            if (failures.size() < MAX_FAILURES) failures.add("SHA256SUMS: invalid UTF-8");
        }
        if (!verifiedFiles.containsAll(EXPECTED_BACKUP_FILES)) {
            if (failures.size() < MAX_FAILURES) failures.add("SHA256SUMS: required backup file missing");
        }
        boolean valid = failures.isEmpty() && verifiedFiles.size() == EXPECTED_BACKUP_FILES.size();
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
            if (!Files.isRegularFile(db, LinkOption.NOFOLLOW_LINKS) || !Files.isRegularFile(docs, LinkOption.NOFOLLOW_LINKS)) return null;
            Instant created = Files.getLastModifiedTime(dir).toInstant();
            return new BackupInfo(dir.getFileName().toString(), created,
                Files.size(db), Files.size(docs), Files.isRegularFile(sums, LinkOption.NOFOLLOW_LINKS));
        } catch (IOException e) {
            return null;
        }
    }

    private Path safeDirectory(String name) {
        if (name == null || name.isBlank() || name.length() > MAX_BACKUP_NAME_LENGTH) {
            throw new IllegalArgumentException("Invalid backup name");
        }
        Path dir = root.resolve(name).normalize();
        if (!dir.startsWith(root) || !dir.getFileName().toString().equals(name))
            throw new IllegalArgumentException("Invalid backup name");
        return dir;
    }

    private boolean hasSymlinkComponent(Path rootPath, Path targetParent) {
        if (targetParent == null || !targetParent.startsWith(rootPath) || Files.isSymbolicLink(rootPath)) return true;
        Path current = rootPath;
        Path relative = rootPath.relativize(targetParent);
        for (Path component : relative) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) return true;
        }
        return false;
    }

    private String safeFailure(String name, String reason) {
        String value = name.length() > MAX_FAILURE_MESSAGE_LENGTH ? name.substring(0, MAX_FAILURE_MESSAGE_LENGTH) : name;
        return value + ": " + reason;
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
