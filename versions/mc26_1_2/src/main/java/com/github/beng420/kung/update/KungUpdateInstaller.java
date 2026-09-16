package com.github.beng420.kung.update;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipFile;

/** JDK-only helper: the installed path changes only through a verified atomic replacement. */
public final class KungUpdateInstaller {
    public static final long MAX_JAR_BYTES = 128L * 1024 * 1024;
    private static final long MAX_EXPANDED_BYTES = 512L * 1024 * 1024;
    private static final int MAX_ENTRIES = 100_000;
    private static final int MAX_MARKER_BYTES = 16 * 1024;
    private static final String MARKER = "pending.properties";

    private KungUpdateInstaller() { }

    public record Plan(Path source, Path target, String sourceSha256, String targetSha256) {
        public Plan {
            source = source.toAbsolutePath().normalize();
            target = target.toAbsolutePath().normalize();
            sourceSha256 = normalizedDigest(sourceSha256);
            targetSha256 = normalizedDigest(targetSha256);
        }
    }

    enum Checkpoint { BEFORE_BACKUP, AFTER_BACKUP, BEFORE_COMMIT, AFTER_COMMIT }

    interface Hooks {
        default void checkpoint(Checkpoint checkpoint) throws IOException { }
        default void replace(Path source, Path target) throws IOException {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static final class DefaultHooks implements Hooks { }

    public static void writePlan(Path directory, Plan plan) throws IOException {
        Path dir = checkedDirectory(directory, true);
        validatePaths(dir, plan);
        try (FileChannel channel = lockChannel(dir.resolve("install.lock")); FileLock lock = tryLock(channel)) {
            Path marker = dir.resolve(MARKER);
            rejectSymlinks(marker);
            if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
                if (readPlan(dir).equals(plan)) return;
                throw new IOException("Another Kung update is already pending.");
            }
            Properties properties = new Properties();
            properties.setProperty("schema", "2");
            properties.setProperty("source", plan.source().toString());
            properties.setProperty("target", plan.target().toString());
            properties.setProperty("sourceSha256", plan.sourceSha256());
            properties.setProperty("targetSha256", plan.targetSha256());
            var bytes = new ByteArrayOutputStream();
            properties.store(bytes, "Kung verified pending update");
            Path temporary = Files.createTempFile(dir, "pending-", ".tmp");
            try {
                writeForced(temporary, bytes.toByteArray());
                Files.move(temporary, marker, StandardCopyOption.ATOMIC_MOVE);
                forceDirectory(dir);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    public static Plan readPlan(Path directory) throws IOException {
        Path dir = checkedDirectory(directory, false);
        Path marker = dir.resolve(MARKER);
        rejectSymlinks(marker);
        requireRegular(marker);
        if (Files.size(marker) > MAX_MARKER_BYTES) throw new IOException("Kung update marker is too large.");
        byte[] bytes;
        try (InputStream input = Files.newInputStream(marker)) {
            bytes = input.readNBytes(MAX_MARKER_BYTES + 1);
        }
        if (bytes.length > MAX_MARKER_BYTES) throw new IOException("Kung update marker is too large.");
        try {
            Properties properties = new Properties();
            properties.load(new ByteArrayInputStream(bytes));
            if (!"2".equals(properties.getProperty("schema"))) throw new IOException("Unsupported Kung update marker schema.");
            Plan plan = new Plan(Path.of(required(properties, "source")), Path.of(required(properties, "target")),
                required(properties, "sourceSha256"), required(properties, "targetSha256"));
            validatePaths(dir, plan);
            return plan;
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid Kung update marker.", exception);
        }
    }

    public static void apply(Path directory, Plan plan) throws IOException {
        apply(directory, plan, new DefaultHooks());
    }

    static void apply(Path directory, Plan plan, Hooks hooks) throws IOException {
        Path dir = checkedDirectory(directory, false);
        validatePaths(dir, plan);
        try (FileChannel channel = lockChannel(dir.resolve("install.lock")); FileLock lock = tryLock(channel)) {
            Path marker = dir.resolve(MARKER);
            rejectSymlinks(marker);
            if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS) && !readPlan(dir).equals(plan)) {
                throw new IOException("The pending Kung update changed; refusing a stale installer.");
            }
            requireRegular(plan.target());
            String installed = sha256(plan.target());
            if (installed.equals(plan.sourceSha256())) {
                Files.deleteIfExists(marker);
                forceDirectory(dir);
                return;
            }
            if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) throw new IOException("No pending Kung update.");
            if (!installed.equals(plan.targetSha256())) throw new IOException("Installed Kung changed after this update was prepared.");
            requireRegular(plan.source());
            requireDigest(plan.source(), plan.sourceSha256());
            validateJar(plan.source());
            validateJar(plan.target());
            Path staged = Files.createTempFile(dir, "install-", ".tmp");
            Path backupTemporary = null;
            try {
                copyForced(plan.source(), staged);
                requireDigest(staged, plan.sourceSha256());
                hooks.checkpoint(Checkpoint.BEFORE_BACKUP);
                Path backup = dir.resolve("previous.jar");
                rejectSymlinks(backup);
                backupTemporary = Files.createTempFile(dir, "previous-", ".tmp");
                copyForced(plan.target(), backupTemporary);
                requireDigest(backupTemporary, plan.targetSha256());
                Files.move(backupTemporary, backup, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                forceDirectory(dir);
                hooks.checkpoint(Checkpoint.AFTER_BACKUP);
                hooks.checkpoint(Checkpoint.BEFORE_COMMIT);
                // A launcher/manual replacement must not be silently overwritten by an old transaction.
                validatePaths(dir, plan);
                requireDigest(plan.target(), plan.targetSha256());
                requireDigest(staged, plan.sourceSha256());
                hooks.replace(staged, plan.target());
                forceDirectory(plan.target().getParent());
                hooks.checkpoint(Checkpoint.AFTER_COMMIT);
                requireDigest(plan.target(), plan.sourceSha256());
                Files.delete(marker);
                forceDirectory(dir);
            } finally {
                Files.deleteIfExists(staged);
                if (backupTemporary != null) Files.deleteIfExists(backupTemporary);
            }
        }
    }

    public static String sha256(Path path) throws IOException {
        rejectSymlinks(path);
        requireRegular(path);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            try (InputStream input = Files.newInputStream(path)) {
                for (int count; (count = input.read(buffer)) != -1;) digest.update(buffer, 0, count);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    /** Opening a ZIP is insufficient: every entry's bytes, size and CRC must agree. */
    public static void validateJar(Path path) throws IOException {
        rejectSymlinks(path);
        requireRegular(path);
        if (Files.size(path) == 0 || Files.size(path) > MAX_JAR_BYTES) throw new IOException("Invalid Kung JAR size.");
        Set<String> names = new HashSet<>();
        long expanded = 0;
        boolean metadata = false;
        byte[] buffer = new byte[64 * 1024];
        try (ZipFile zip = new ZipFile(path.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (names.size() >= MAX_ENTRIES || !names.add(entry.getName())) throw new IOException("Invalid or excessive Kung JAR entries.");
                if (entry.getSize() < 0 || entry.getSize() > MAX_EXPANDED_BYTES - expanded) throw new IOException("Kung JAR expands beyond the size limit.");
                if (entry.isDirectory() && entry.getSize() != 0) throw new IOException("Invalid Kung JAR directory entry.");
                long count = 0;
                CRC32 crc = new CRC32();
                try (InputStream input = zip.getInputStream(entry)) {
                    for (int read; (read = input.read(buffer)) != -1;) {
                        count += read;
                        expanded += read;
                        if (expanded > MAX_EXPANDED_BYTES || count > entry.getSize()) throw new IOException("Kung JAR expands beyond its declared size.");
                        crc.update(buffer, 0, read);
                    }
                }
                if (count != entry.getSize() || crc.getValue() != entry.getCrc()) throw new IOException("Corrupt Kung JAR entry: " + entry.getName());
                if (entry.getName().equals("fabric.mod.json") && !entry.isDirectory()) metadata = true;
            }
        }
        if (!metadata) throw new IOException("Kung JAR has no Fabric metadata.");
    }

    public static void main(String[] args) {
        try {
            if (args.length != 3) throw new IOException("Expected parent PID, parent start milliseconds and update directory.");
            long pid = Long.parseLong(args[0]);
            long startMillis = Long.parseLong(args[1]);
            Path dir = checkedDirectory(Path.of(args[2]), false);
            Instant deadline = Instant.now().plus(Duration.ofHours(24));
            while (sameProcessAlive(pid, startMillis)) {
                if (Instant.now().isAfter(deadline)) throw new IOException("Timed out waiting for Minecraft to exit.");
                Thread.sleep(500);
            }
            // A fast restart owns this lock for its entire JVM lifetime; it will schedule a fresh helper.
            try (FileChannel channel = lockChannel(dir.resolve("session.lock")); FileLock lock = tryLock(channel)) {
                apply(dir, readPlan(dir));
            }
            System.out.println("Kung update installed successfully.");
        } catch (Exception exception) {
            System.err.println("Kung update remains pending: " + exception.getMessage());
            System.exit(1);
        }
    }

    private static boolean sameProcessAlive(long pid, long startMillis) throws IOException {
        var process = ProcessHandle.of(pid);
        if (process.isEmpty() || !process.get().isAlive()) return false;
        var start = process.get().info().startInstant();
        if (start.isEmpty()) throw new IOException("Cannot verify Minecraft process identity.");
        return start.get().toEpochMilli() == startMillis;
    }

    private static Path checkedDirectory(Path directory, boolean create) throws IOException {
        Path dir = directory.toAbsolutePath().normalize();
        rejectSymlinks(dir);
        if (create) Files.createDirectories(dir);
        rejectSymlinks(dir);
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing Kung update directory.");
        return dir;
    }

    private static void validatePaths(Path dir, Plan plan) throws IOException {
        rejectSymlinks(dir);
        rejectSymlinks(plan.source());
        rejectSymlinks(plan.target());
        Path parent = plan.target().getParent();
        if (!dir.equals(plan.source().getParent()) || !isJarName(plan.source())
            || plan.source().getFileName().toString().equals("previous.jar")
            || parent == null || parent.getFileName() == null || !parent.getFileName().toString().equals("mods")
            || !isJarName(plan.target()) || plan.source().equals(plan.target())) {
            throw new IOException("Unsafe Kung update paths.");
        }
    }

    private static boolean isJarName(Path path) {
        return path.getFileName() != null && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar");
    }

    private static void rejectSymlinks(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        for (Path component = absolute; component != null; component = component.getParent()) {
            if (Files.isSymbolicLink(component)) throw new IOException("Symbolic link is not allowed for Kung updates: " + component);
        }
    }

    private static void requireRegular(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing regular file: " + path);
    }

    private static String required(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) throw new IOException("Missing Kung update field: " + key);
        return value;
    }

    private static String normalizedDigest(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{64}")) throw new IllegalArgumentException("Invalid SHA-256 digest.");
        return value.toLowerCase(Locale.ROOT);
    }

    private static void requireDigest(Path path, String expected) throws IOException {
        if (!sha256(path).equals(expected)) throw new IOException("Kung update checksum changed: " + path);
    }

    private static FileChannel lockChannel(Path path) throws IOException {
        rejectSymlinks(path);
        return FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
    }

    private static FileLock tryLock(FileChannel channel) throws IOException {
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) throw new IOException("Another Kung session or installer is active.");
            return lock;
        } catch (OverlappingFileLockException exception) {
            throw new IOException("Another Kung session or installer is active.", exception);
        }
    }

    private static void writeForced(Path target, byte[] bytes) throws IOException {
        try (FileChannel output = FileChannel.open(target, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) output.write(buffer);
            output.force(true);
        }
    }

    private static void copyForced(Path source, Path target) throws IOException {
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        try (FileChannel output = FileChannel.open(target, StandardOpenOption.WRITE)) {
            output.force(true);
        }
    }

    private static void forceDirectory(Path directory) {
        // Some systems (notably Windows) cannot fsync directories. Atomicity cannot guarantee disk survival after power loss.
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException ignored) { }
    }
}
