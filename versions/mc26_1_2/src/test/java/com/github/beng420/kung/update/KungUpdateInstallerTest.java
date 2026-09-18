package com.github.beng420.kung.update;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class KungUpdateInstallerTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void replacementKeepsExactFilenameAndPreservesSourceAndOldBackup() throws Exception {
        Fixture f = fixture();
        byte[] old = Files.readAllBytes(f.target);
        byte[] update = Files.readAllBytes(f.source);
        KungUpdateInstaller.apply(f.dir, f.plan);
        assertArrayEquals(update, Files.readAllBytes(f.target));
        assertArrayEquals(update, Files.readAllBytes(f.source));
        assertArrayEquals(old, Files.readAllBytes(f.dir.resolve("previous.jar")));
        assertFalse(Files.exists(f.marker()));
        try (var files = Files.list(f.target.getParent())) {
            assertEquals(1, files.count());
        }
        KungUpdateInstaller.apply(f.dir, f.plan);
        assertArrayEquals(old, Files.readAllBytes(f.dir.resolve("previous.jar")));
    }

    @Test public void replacementDoesNotChangeALauncherCacheOrAnotherProfilesHardLink() throws Exception {
        Fixture f = fixture();
        byte[] old = Files.readAllBytes(f.target);
        Path cached = Files.createLink(temp.getRoot().toPath().resolve("launcher-cache.jar"), f.target);
        Path other = Files.createLink(temp.getRoot().toPath().resolve("other-profile.jar"), f.target);
        assertTrue(Files.isSameFile(cached, f.target));
        KungUpdateInstaller.apply(f.dir, f.plan);
        assertArrayEquals(Files.readAllBytes(f.source), Files.readAllBytes(f.target));
        assertArrayEquals(old, Files.readAllBytes(cached));
        assertArrayEquals(old, Files.readAllBytes(other));
        assertArrayEquals(old, Files.readAllBytes(f.dir.resolve("previous.jar")));
        assertFalse(Files.isSameFile(cached, f.target));
        assertFalse(Files.exists(f.marker()));
    }

    @Test public void failuresAtEachTransactionBoundaryLeaveACompleteJarAndCanResume() throws Exception {
        for (var point : KungUpdateInstaller.Checkpoint.values()) {
            Fixture f = fixture();
            byte[] old = Files.readAllBytes(f.target);
            byte[] update = Files.readAllBytes(f.source);
            assertThrows(IOException.class, () -> KungUpdateInstaller.apply(f.dir, f.plan, new KungUpdateInstaller.Hooks() {
                @Override public void checkpoint(KungUpdateInstaller.Checkpoint current) throws IOException {
                    if (point == current) throw new IOException("Simulated interruption at " + point);
                }
            }));
            assertTrue(Files.exists(f.marker()));
            assertArrayEquals(point == KungUpdateInstaller.Checkpoint.AFTER_COMMIT ? update : old, Files.readAllBytes(f.target));
            KungUpdateInstaller.validateJar(f.target);
            KungUpdateInstaller.apply(f.dir, KungUpdateInstaller.readPlan(f.dir));
            assertArrayEquals(update, Files.readAllBytes(f.target));
            assertFalse(Files.exists(f.marker()));
        }
    }

    @Test public void unavailableAtomicReplacementNeverFallsBackToDestructiveCopy() throws Exception {
        Fixture f = fixture();
        byte[] old = Files.readAllBytes(f.target);
        assertThrows(AtomicMoveNotSupportedException.class, () -> KungUpdateInstaller.apply(f.dir, f.plan, new KungUpdateInstaller.Hooks() {
            @Override public void replace(Path source, Path target) throws IOException {
                throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "Cross-device");
            }
        }));
        assertArrayEquals(old, Files.readAllBytes(f.target));
        assertTrue(Files.exists(f.source));
        assertTrue(Files.exists(f.marker()));
    }

    @Test public void changedInstalledJarIsPreservedInsteadOfOverwritten() throws Exception {
        Fixture f = fixture();
        jar(f.target, "manually installed newer version");
        byte[] changed = Files.readAllBytes(f.target);
        assertThrows(IOException.class, () -> KungUpdateInstaller.apply(f.dir, f.plan));
        assertArrayEquals(changed, Files.readAllBytes(f.target));
        assertTrue(Files.exists(f.marker()));
        assertFalse(Files.exists(f.dir.resolve("previous.jar")));
    }

    @Test public void installedJarChangedJustBeforeCommitIsPreserved() throws Exception {
        Fixture f = fixture();
        assertThrows(IOException.class, () -> KungUpdateInstaller.apply(f.dir, f.plan, new KungUpdateInstaller.Hooks() {
            @Override public void checkpoint(KungUpdateInstaller.Checkpoint checkpoint) throws IOException {
                if (checkpoint == KungUpdateInstaller.Checkpoint.BEFORE_COMMIT) jar(f.target, "manual replacement during staging");
            }
        }));
        assertNotEquals(f.plan.targetSha256(), KungUpdateInstaller.sha256(f.target));
        assertNotEquals(f.plan.sourceSha256(), KungUpdateInstaller.sha256(f.target));
        assertTrue(Files.exists(f.marker()));
    }

    @Test public void missingAndTamperedSourceLeaveInstalledJarUntouched() throws Exception {
        for (boolean missing : new boolean[] {false, true}) {
            Fixture f = fixture();
            byte[] old = Files.readAllBytes(f.target);
            if (missing) Files.delete(f.source);
            else Files.writeString(f.source, "interrupted download");
            assertThrows(IOException.class, () -> KungUpdateInstaller.apply(f.dir, f.plan));
            assertArrayEquals(old, Files.readAllBytes(f.target));
            assertTrue(Files.exists(f.marker()));
        }
    }

    @Test public void matchingHashDoesNotPermitCorruptJar() throws Exception {
        Fixture f = fixture();
        Files.delete(f.marker());
        Files.writeString(f.source, "HTTP 200 but this is not a JAR");
        var invalid = new KungUpdateInstaller.Plan(f.source, f.target, KungUpdateInstaller.sha256(f.source), f.plan.targetSha256());
        KungUpdateInstaller.writePlan(f.dir, invalid);
        assertThrows(IOException.class, () -> KungUpdateInstaller.apply(f.dir, invalid));
        assertEquals(f.plan.targetSha256(), KungUpdateInstaller.sha256(f.target));
    }

    @Test public void completedCommitCanClearMarkerEvenWhenDownloadedSourceIsGone() throws Exception {
        Fixture f = fixture();
        Files.copy(f.source, f.target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.delete(f.source);
        KungUpdateInstaller.apply(f.dir, f.plan);
        assertEquals(f.plan.sourceSha256(), KungUpdateInstaller.sha256(f.target));
        assertFalse(Files.exists(f.marker()));
    }

    @Test public void missingTargetIsNeverRecreatedByStalePendingUpdate() throws Exception {
        Fixture f = fixture();
        Files.delete(f.target);
        assertThrows(IOException.class, () -> KungUpdateInstaller.apply(f.dir, f.plan));
        assertFalse(Files.exists(f.target));
        assertTrue(Files.exists(f.marker()));
    }

    @Test public void markerRoundTripAndPendingTransactionCannotBeOverwritten() throws Exception {
        Fixture f = fixture();
        assertEquals(f.plan, KungUpdateInstaller.readPlan(f.dir));
        byte[] marker = Files.readAllBytes(f.marker());
        KungUpdateInstaller.writePlan(f.dir, f.plan);
        assertArrayEquals(marker, Files.readAllBytes(f.marker()));
        Path another = jar(f.dir.resolve("kung-another.jar"), "another update");
        var different = new KungUpdateInstaller.Plan(another, f.target, KungUpdateInstaller.sha256(another), f.plan.targetSha256());
        assertThrows(IOException.class, () -> KungUpdateInstaller.writePlan(f.dir, different));
        assertThrows(IOException.class, () -> KungUpdateInstaller.apply(f.dir, different));
        assertArrayEquals(marker, Files.readAllBytes(f.marker()));
        assertEquals(f.plan.targetSha256(), KungUpdateInstaller.sha256(f.target));
    }

    @Test public void partialLegacyAndOversizedMarkersAreRejectedWithoutDeletingThem() throws Exception {
        Fixture f = fixture();
        for (String invalid : new String[] {"source=partial", "schema=1\nsource=old.jar\ntarget=old.jar", "schema=2\nsource=\\uZZZZ", "x".repeat(16 * 1024 + 1)}) {
            Files.writeString(f.marker(), invalid);
            assertThrows(IOException.class, () -> KungUpdateInstaller.readPlan(f.dir));
            assertEquals(invalid, Files.readString(f.marker()));
            assertEquals(f.plan.targetSha256(), KungUpdateInstaller.sha256(f.target));
        }
    }

    @Test public void refusesPathsOutsideExpectedDirectoriesAndReservedBackup() throws Exception {
        Fixture f = fixture();
        Path outside = jar(temp.newFolder().toPath().resolve("outside.jar"), "outside");
        for (var bad : new KungUpdateInstaller.Plan[] {
            new KungUpdateInstaller.Plan(outside, f.target, f.plan.sourceSha256(), f.plan.targetSha256()),
            new KungUpdateInstaller.Plan(f.source, outside, f.plan.sourceSha256(), f.plan.targetSha256()),
            new KungUpdateInstaller.Plan(f.dir.resolve("previous.jar"), f.target, f.plan.sourceSha256(), f.plan.targetSha256()),
            new KungUpdateInstaller.Plan(f.dir.resolve("nested/update.jar"), f.target, f.plan.sourceSha256(), f.plan.targetSha256())
        }) {
            assertThrows(IOException.class, () -> KungUpdateInstaller.apply(f.dir, bad));
        }
        assertEquals(f.plan.targetSha256(), KungUpdateInstaller.sha256(f.target));
    }

    @Test public void concurrentInstallerCannotEnterTransaction() throws Exception {
        Fixture f = fixture();
        try (FileChannel channel = FileChannel.open(f.dir.resolve("install.lock"), StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            assertThrows(IOException.class, () -> KungUpdateInstaller.apply(f.dir, f.plan));
        }
        assertEquals(f.plan.targetSha256(), KungUpdateInstaller.sha256(f.target));
        assertTrue(Files.exists(f.marker()));
    }

    @Test public void symlinkedSourceAndTargetAreRejectedWhenPlatformAllowsCreation() throws Exception {
        Fixture f = fixture();
        Path real = jar(temp.newFolder().toPath().resolve("real.jar"), "outside");
        String outsideHash = KungUpdateInstaller.sha256(real);
        Path link = f.dir.resolve("linked.jar");
        try {
            Files.createSymbolicLink(link, real);
        } catch (IOException | UnsupportedOperationException | SecurityException unavailable) {
            // Windows may deny symlink creation without Developer Mode; the independent path checks still run above.
            org.junit.Assume.assumeNoException("Platform cannot create a symbolic-link fixture.", unavailable);
            return;
        }
        var linked = new KungUpdateInstaller.Plan(link, f.target, KungUpdateInstaller.sha256(real), f.plan.targetSha256());
        assertThrows(IOException.class, () -> KungUpdateInstaller.apply(f.dir, linked));
        Files.delete(f.target);
        Files.createSymbolicLink(f.target, real);
        assertThrows(IOException.class, () -> KungUpdateInstaller.apply(f.dir, f.plan));
        assertEquals(outsideHash, KungUpdateInstaller.sha256(real));
    }

    @Test public void zipValidationReadsEntriesAndChecksCrcRatherThanOnlyCentralDirectory() throws Exception {
        Path path = jar(temp.newFolder().toPath().resolve("bad.jar"), "UNIQUE_PAYLOAD_FOR_CRC_CHECK");
        byte[] bytes = Files.readAllBytes(path);
        byte[] payload = "UNIQUE_PAYLOAD_FOR_CRC_CHECK".getBytes(StandardCharsets.UTF_8);
        int offset = indexOf(bytes, payload);
        assertTrue(offset >= 0);
        bytes[offset] ^= 1;
        Files.write(path, bytes);
        assertThrows(IOException.class, () -> KungUpdateInstaller.validateJar(path));
    }

    @Test public void zipValidationRejectsMissingMetadataAndDataInDirectoryEntries() throws Exception {
        Path path = temp.newFolder().toPath().resolve("bad.jar");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            storedEntry(output, "classes/", "data in a directory");
            storedEntry(output, "fabric.mod.json", "{}");
        }
        assertThrows(IOException.class, () -> KungUpdateInstaller.validateJar(path));
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            storedEntry(output, "Other.class", "other mod");
        }
        assertThrows(IOException.class, () -> KungUpdateInstaller.validateJar(path));
    }

    private Fixture fixture() throws Exception {
        Path root = temp.newFolder().toPath();
        Path dir = Files.createDirectories(root.resolve("config/kung/updates"));
        Path target = jar(Files.createDirectories(root.resolve("mods")).resolve("kung-old-filename.jar"), "old Kung");
        Path source = jar(dir.resolve("kung-new-filename.jar"), "new Kung");
        var plan = new KungUpdateInstaller.Plan(source, target, KungUpdateInstaller.sha256(source), KungUpdateInstaller.sha256(target));
        KungUpdateInstaller.writePlan(dir, plan);
        return new Fixture(dir, source, target, plan);
    }

    private static Path jar(Path file, String payload) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(file))) {
            storedEntry(output, "fabric.mod.json", "{\"id\":\"kung\",\"version\":\"0.3.4\"}");
            storedEntry(output, "Example.class", payload);
        }
        return file;
    }

    private static void storedEntry(ZipOutputStream output, String name, String payload) throws IOException {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        CRC32 crc = new CRC32();
        crc.update(bytes);
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(bytes.length);
        entry.setCompressedSize(bytes.length);
        entry.setCrc(crc.getValue());
        output.putNextEntry(entry);
        output.write(bytes);
        output.closeEntry();
    }

    private static int indexOf(byte[] bytes, byte[] search) {
        for (int index = 0; index <= bytes.length - search.length; index++) {
            if (Arrays.equals(bytes, index, index + search.length, search, 0, search.length)) return index;
        }
        return -1;
    }

    private record Fixture(Path dir, Path source, Path target, KungUpdateInstaller.Plan plan) {
        Path marker() { return dir.resolve("pending.properties"); }
    }
}
