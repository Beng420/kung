package com.github.beng420.kung.update;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/** The helper runs from its own JAR, so it never locks the mod being replaced. */
final class KungUpdateProcess {
    private FileChannel sessionChannel;
    private FileLock sessionLock;

    void holdSession(Path directory) throws IOException {
        if (sessionLock != null && sessionLock.isValid()) return;
        rejectLinks(directory);
        Files.createDirectories(directory);
        Path lock = directory.resolve("session.lock");
        rejectLinks(lock);
        sessionChannel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            sessionLock = sessionChannel.tryLock();
            if (sessionLock == null) throw new IOException("Another game or installer is using this update directory.");
        } catch (IOException | RuntimeException exception) {
            sessionChannel.close();
            throw exception;
        }
        // Kept until JVM exit: CLIENT_STOPPING still leaves Fabric's JAR resources in use.
    }

    void launch(Path directory, Path logDirectory) throws IOException {
        if (sessionLock == null || !sessionLock.isValid()) throw new IOException("Missing game session lock.");
        Path helper = packageHelper(directory);
        rejectLinks(logDirectory);
        Files.createDirectories(logDirectory);
        Path log = logDirectory.resolve("update-installer.log");
        rejectLinks(log);
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        Path java = Path.of(System.getProperty("java.home"), "bin", windows ? "javaw.exe" : "java");
        long start = ProcessHandle.current().info().startInstant()
            .orElseThrow(() -> new IOException("Cannot identify the game process.")).toEpochMilli();
        new ProcessBuilder(java.toString(), "-cp", helper.toString(), KungUpdateInstaller.class.getName(),
            Long.toString(ProcessHandle.current().pid()), Long.toString(start), directory.toString())
            .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile())).start();
    }

    static Path packageHelper(Path directory) throws IOException {
        rejectLinks(directory);
        Path temporary = Files.createTempFile(directory, "installer-", ".tmp");
        try {
            try (var output = new JarOutputStream(Files.newOutputStream(temporary))) {
                addClass(output, KungUpdateInstaller.class);
            }
            try (var file = FileChannel.open(temporary, StandardOpenOption.WRITE)) { file.force(true); }
            String digest = KungUpdateInstaller.sha256(temporary);
            Path helper = directory.resolve("installer-" + digest + ".jar");
            rejectLinks(helper);
            if (Files.exists(helper)) {
                if (!KungUpdateInstaller.sha256(helper).equals(digest)) throw new IOException("Installer checksum mismatch.");
            } else {
                Files.move(temporary, helper, StandardCopyOption.ATOMIC_MOVE);
            }
            return helper;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void addClass(JarOutputStream output, Class<?> type) throws IOException {
        String name = type.getName().replace('.', '/') + ".class";
        try (var input = type.getResourceAsStream("/" + name)) {
            if (input == null) throw new IOException("Missing installer class: " + name);
            JarEntry entry = new JarEntry(name);
            entry.setTime(0);
            output.putNextEntry(entry);
            input.transferTo(output);
            output.closeEntry();
        }
        for (Class<?> nested : type.getDeclaredClasses()) addClass(output, nested);
    }

    static void rejectLinks(Path path) throws IOException {
        for (Path part = path.toAbsolutePath().normalize(); part != null; part = part.getParent()) {
            if (Files.isSymbolicLink(part)) throw new IOException("Automatic updates do not follow symbolic links.");
        }
    }
}
