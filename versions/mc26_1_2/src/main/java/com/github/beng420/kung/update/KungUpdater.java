package com.github.beng420.kung.update;

import com.github.beng420.kung.KungMod;
import com.github.beng420.kung.message.KungMessages;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.client.Minecraft;

public enum KungUpdater {
    INSTANCE;

    private static final String RELEASE_API = "https://api.github.com/repos/Beng420/kung/releases/latest";
    private static final Pattern VERSION_PATTERN = Pattern.compile("^v?(\\d+(?:\\.\\d+){0,3}).*$", Pattern.CASE_INSENSITIVE);
    private static final List<String> SKIPPED_ASSET_PARTS = List.of("-sources", "-javadoc", "-dev", "-all");

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Kung Updater");
        thread.setDaemon(true);
        return thread;
    });
    private final HttpClient httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .connectTimeout(Duration.ofSeconds(8))
        .build();
    private final AtomicBoolean checking = new AtomicBoolean();
    private final AtomicBoolean installing = new AtomicBoolean();
    private final AtomicReference<State> state = new AtomicReference<>(State.checking(currentVersion()));

    public void checkForUpdatesAsync() {
        Status status = state.get().status();
        if (status == Status.UPDATE_AVAILABLE || status == Status.DOWNLOADING || status == Status.INSTALL_READY) {
            return;
        }
        if (!checking.compareAndSet(false, true)) {
            return;
        }

        state.set(State.checking(currentVersion()));
        executor.execute(() -> {
            try {
                state.set(checkForUpdates());
            } catch (Exception exception) {
                KungMod.LOGGER.warn("Kung update check failed.", exception);
                state.set(new State(
                    Status.FAILED,
                    currentVersion(),
                    "",
                    "Update check failed",
                    null
                ));
            } finally {
                checking.set(false);
            }
        });
    }

    public void installPendingUpdateIfReady() {
        Path marker = pendingMarkerPath();
        if (!Files.isRegularFile(marker)) {
            return;
        }

        try {
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(marker)) {
                properties.load(input);
            }

            String sourceValue = properties.getProperty("source");
            String targetValue = properties.getProperty("target");
            if (sourceValue == null || targetValue == null) {
                Files.deleteIfExists(marker);
                return;
            }

            Path source = Path.of(sourceValue);
            Path target = Path.of(targetValue);
            if (!Files.isRegularFile(source)) {
                Files.deleteIfExists(marker);
                return;
            }

            installDownloadedJar(source, target);
            Files.deleteIfExists(marker);
            KungMod.LOGGER.info("Installed pending Kung update to {}.", target);
        } catch (IOException exception) {
            KungMod.LOGGER.warn("Could not install pending Kung update yet.", exception);
        }
    }

    public boolean isUpdateAvailable() {
        return state.get().status() == Status.UPDATE_AVAILABLE;
    }

    public boolean canInstallUpdate() {
        return state.get().status() == Status.UPDATE_AVAILABLE && !installing.get();
    }

    public String buttonLabel() {
        return switch (state.get().status()) {
            case CHECKING -> "Updates: Checking...";
            case UP_TO_DATE -> "Updates: Up to date";
            case UPDATE_AVAILABLE -> "Updates: Available";
            case DOWNLOADING -> "Updates: Downloading...";
            case INSTALL_READY -> "Updates: Restart needed";
            case UNSUPPORTED -> "Updates: Dev build";
            case FAILED -> "Updates: Failed";
        };
    }

    public String statusMessage() {
        State snapshot = state.get();
        return switch (snapshot.status()) {
            case CHECKING -> "Checking GitHub";
            case UP_TO_DATE -> "Version " + snapshot.currentVersion();
            case UPDATE_AVAILABLE -> "Installs " + snapshot.latestVersion();
            case DOWNLOADING -> "Downloading...";
            case INSTALL_READY -> "Restart Minecraft";
            case UNSUPPORTED -> snapshot.message();
            case FAILED -> snapshot.message();
        };
    }

    public void installLatestAsync(Minecraft client) {
        State snapshot = state.get();
        if (snapshot.status() != Status.UPDATE_AVAILABLE || snapshot.updateInfo() == null) {
            return;
        }
        if (!installing.compareAndSet(false, true)) {
            return;
        }

        state.set(snapshot.withStatus(Status.DOWNLOADING, "Downloading update"));
        executor.execute(() -> {
            try {
                Path currentJar = currentModJar()
                    .orElseThrow(() -> new IOException("Current mod path is not a jar file."));
                Path downloadedJar = downloadUpdate(snapshot.updateInfo());
                writePendingMarker(downloadedJar, currentJar, snapshot.updateInfo().version());
                launchInstaller(downloadedJar, currentJar);
                state.set(snapshot.withStatus(Status.INSTALL_READY, "Restart Minecraft"));
                KungMessages.send(
                    client,
                    KungMessages.Type.SUCCESS,
                    "Updater",
                    "Update installed. Close Minecraft and start it again."
                );
            } catch (Exception exception) {
                KungMod.LOGGER.warn("Kung update install failed.", exception);
                state.set(new State(
                    Status.FAILED,
                    currentVersion(),
                    snapshot.latestVersion(),
                    "Update installation failed",
                    snapshot.updateInfo()
                ));
                KungMessages.send(
                    client,
                    KungMessages.Type.ERROR,
                    "Updater",
                    "The update could not be installed. See latest.log."
                );
            } finally {
                installing.set(false);
            }
        });
    }

    private State checkForUpdates() throws IOException, InterruptedException {
        String currentVersion = currentVersion();
        String minecraftVersion = minecraftVersion();
        HttpRequest request = HttpRequest.newBuilder(URI.create(RELEASE_API))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Kung-Updater")
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            return new State(Status.UP_TO_DATE, currentVersion, "", "No releases found", null);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub returned HTTP " + response.statusCode());
        }

        JsonObject release = JsonParser.parseString(response.body()).getAsJsonObject();
        String tagName = stringValue(release, "tag_name");
        String latestVersion = releaseVersion(tagName);
        if (!isNewerVersion(latestVersion, currentVersion)) {
            return new State(Status.UP_TO_DATE, currentVersion, latestVersion, "Version " + currentVersion, null);
        }

        Optional<UpdateAsset> asset = findCompatibleJar(release, minecraftVersion);
        if (asset.isEmpty()) {
            return new State(
                Status.UNSUPPORTED,
                currentVersion,
                latestVersion,
                "No compatible jar for Minecraft " + minecraftVersion,
                null
            );
        }

        UpdateInfo updateInfo = new UpdateInfo(latestVersion, asset.get().name(), asset.get().downloadUrl());
        return new State(
            Status.UPDATE_AVAILABLE,
            currentVersion,
            latestVersion,
            "Update " + latestVersion + " available",
            updateInfo
        );
    }

    private Path downloadUpdate(UpdateInfo updateInfo) throws IOException, InterruptedException {
        Path updateDirectory = updateDirectory();
        Files.createDirectories(updateDirectory);

        String assetName = sanitizeFileName(updateInfo.assetName());
        Path target = updateDirectory.resolve(assetName);
        Path temporary = updateDirectory.resolve(assetName + ".tmp");
        Files.deleteIfExists(temporary);

        HttpRequest request = HttpRequest.newBuilder(URI.create(updateInfo.downloadUrl()))
            .timeout(Duration.ofMinutes(2))
            .header("User-Agent", "Kung-Updater")
            .GET()
            .build();
        HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(temporary));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            Files.deleteIfExists(temporary);
            throw new IOException("GitHub asset returned HTTP " + response.statusCode());
        }

        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private void writePendingMarker(Path source, Path target, String version) throws IOException {
        Files.createDirectories(updateDirectory());
        Properties properties = new Properties();
        properties.setProperty("source", source.toAbsolutePath().toString());
        properties.setProperty("target", target.toAbsolutePath().toString());
        properties.setProperty("version", version);
        try (OutputStream output = Files.newOutputStream(pendingMarkerPath())) {
            properties.store(output, "Kung pending update");
        }
    }

    private void launchInstaller(Path source, Path target) throws IOException {
        long pid = ProcessHandle.current().pid();
        Path marker = pendingMarkerPath();
        Path script = updateDirectory().resolve(isWindows() ? "install-kung-update.cmd" : "install-kung-update.sh");
        Files.writeString(script, installerScript(pid, source, target, marker), StandardCharsets.UTF_8);

        if (isWindows()) {
            new ProcessBuilder("cmd.exe", "/c", "start", "", "/min", script.toAbsolutePath().toString()).start();
        } else {
            script.toFile().setExecutable(true);
            new ProcessBuilder("sh", script.toAbsolutePath().toString()).start();
        }
    }

    private String installerScript(long pid, Path source, Path target, Path marker) {
        if (isWindows()) {
            return windowsInstallerScript(pid, source, target, marker);
        }
        return unixInstallerScript(pid, source, target, marker);
    }

    private String windowsInstallerScript(long pid, Path source, Path target, Path marker) {
        Path targetDir = target.getParent(); // <-- Diese Zeile hat gefehlt
        return String.join("\r\n",
            "@echo off",
            "setlocal",
            "set \"PID=" + pid + "\"",
            "set \"SOURCE=" + windowsScriptPath(source) + "\"",
            "set \"TARGET=" + windowsScriptPath(target) + "\"",
            "set \"TARGET_DIR=" + windowsScriptPath(targetDir) + "\"",
            "set \"MARKER=" + windowsScriptPath(marker) + "\"",
            ":wait",
            "tasklist /FI \"PID eq %PID%\" 2>NUL | findstr /R /C:\"[ ]%PID%[ ]\" >NUL",
            "if \"%ERRORLEVEL%\"==\"0\" (",
            "  timeout /T 1 /NOBREAK >NUL",
            "  goto wait",
            ")",
            "timeout /T 1 /NOBREAK >NUL",
            "if exist \"%TARGET%\" del /F /Q \"%TARGET%\" >NUL 2>NUL",
            "move /Y \"%SOURCE%\" \"%TARGET_DIR%\\\" >NUL",
            "if errorlevel 1 goto fallback_copy",
            "del /F /Q \"%MARKER%\" >NUL 2>NUL",
            "exit /b 0",
            ":fallback_copy",
            "copy /Y \"%SOURCE%\" \"%TARGET_DIR%\\\" >NUL",
            "if errorlevel 1 exit /b 1",
            "del /F /Q \"%SOURCE%\" >NUL 2>NUL",
            "del /F /Q \"%MARKER%\" >NUL 2>NUL",
            "exit /b 0",
            ""
        );
    }

    private String unixInstallerScript(long pid, Path source, Path target, Path marker) {
        return String.join("\n",
            "#!/bin/sh",
            "PID='" + pid + "'",
            "SOURCE='" + unixScriptPath(source) + "'",
            "TARGET='" + unixScriptPath(target) + "'",
            "MARKER='" + unixScriptPath(marker) + "'",
            "while kill -0 \"$PID\" 2>/dev/null; do sleep 1; done",
            "BACKUP=\"$TARGET.old\"",
            "rm -f \"$BACKUP\"",
            "if mv \"$TARGET\" \"$BACKUP\"; then",
            "  if mv \"$SOURCE\" \"$TARGET\"; then",
            "    rm -f \"$MARKER\" \"$BACKUP\"",
            "    exit 0",
            "  fi",
            "  mv \"$BACKUP\" \"$TARGET\"",
            "  exit 1",
            "fi",
            "cp \"$SOURCE\" \"$TARGET\" && rm -f \"$SOURCE\" \"$MARKER\"",
            ""
        );
    }

    private void installDownloadedJar(Path source, Path target) throws IOException {
        Path finalDestination = target.resolveSibling(source.getFileName());
        if (!finalDestination.equals(target)) {
            Files.deleteIfExists(target);
        }
        Files.move(source, finalDestination, StandardCopyOption.REPLACE_EXISTING);
    }

    private Optional<UpdateAsset> findCompatibleJar(JsonObject release, String minecraftVersion) {
        if (!release.has("assets") || !release.get("assets").isJsonArray()) {
            return Optional.empty();
        }

        JsonArray assets = release.getAsJsonArray("assets");
        List<UpdateAsset> installableAssets = new ArrayList<>();
        for (JsonElement element : assets) {
            if (!element.isJsonObject()) {
                continue;
            }
            Optional<UpdateAsset> asset = assetFromJson(element.getAsJsonObject());
            if (asset.isPresent() && isInstallableJar(asset.get().name())) {
                installableAssets.add(asset.get());
            }
        }

        Optional<UpdateAsset> minecraftAsset = installableAssets.stream()
            .filter(asset -> asset.name().contains(minecraftVersion))
            .findFirst();
        if (minecraftAsset.isPresent()) {
            return minecraftAsset;
        }

        if (minecraftVersion.isBlank() && installableAssets.size() == 1) {
            return Optional.of(installableAssets.getFirst());
        }
        return Optional.empty();
    }

    private static Optional<UpdateAsset> assetFromJson(JsonObject object) {
        String name = stringValue(object, "name");
        String downloadUrl = stringValue(object, "browser_download_url");
        if (name.isBlank() || downloadUrl.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new UpdateAsset(name, downloadUrl));
    }

    private static boolean isInstallableJar(String name) {
        String lowerName = name.toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".jar")) {
            return false;
        }
        for (String skippedPart : SKIPPED_ASSET_PARTS) {
            if (lowerName.contains(skippedPart)) {
                return false;
            }
        }
        return true;
    }

    private Optional<Path> currentModJar() {
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(KungMod.MOD_ID);
        if (container.isEmpty()) {
            return Optional.empty();
        }

        ModOrigin origin = container.get().getOrigin();
        if (origin.getKind() == ModOrigin.Kind.PATH) {
            for (Path path : origin.getPaths()) {
                if (isJarFile(path)) {
                    return Optional.of(path.toAbsolutePath());
                }
            }
        }

        for (Path rootPath : container.get().getRootPaths()) {
            if (isJarFile(rootPath)) {
                return Optional.of(rootPath.toAbsolutePath());
            }
        }
        return Optional.empty();
    }

    private static boolean isJarFile(Path path) {
        return Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar");
    }

    private static String currentVersion() {
        return FabricLoader.getInstance()
            .getModContainer(KungMod.MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("0.0.0");
    }

    private static String minecraftVersion() {
        return FabricLoader.getInstance()
            .getModContainer("minecraft")
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("");
    }

    private static String releaseVersion(String tagName) {
        String trimmed = tagName == null ? "" : tagName.trim();
        Matcher matcher = VERSION_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return trimmed.startsWith("v") || trimmed.startsWith("V") ? trimmed.substring(1) : trimmed;
    }

    private static boolean isNewerVersion(String latestVersion, String currentVersion) {
        List<Integer> latestParts = numericVersionParts(latestVersion);
        List<Integer> currentParts = numericVersionParts(currentVersion);
        int max = Math.max(latestParts.size(), currentParts.size());
        for (int index = 0; index < max; index++) {
            int latest = index < latestParts.size() ? latestParts.get(index) : 0;
            int current = index < currentParts.size() ? currentParts.get(index) : 0;
            if (latest != current) {
                return latest > current;
            }
        }
        return false;
    }

    private static List<Integer> numericVersionParts(String version) {
        return Pattern.compile("\\d+")
            .matcher(version == null ? "" : version)
            .results()
            .map(result -> Integer.parseInt(result.group()))
            .toList();
    }

    private static String stringValue(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        return object.get(key).getAsString();
    }

    private static Path updateDirectory() {
        return FabricLoader.getInstance().getGameDir().resolve("kung-updates");
    }

    private static Path pendingMarkerPath() {
        return updateDirectory().resolve("pending.properties");
    }

    private static String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String windowsScriptPath(Path path) {
        return path.toAbsolutePath().toString().replace("%", "%%");
    }

    private static String unixScriptPath(Path path) {
        return path.toAbsolutePath().toString().replace("'", "'\"'\"'");
    }

    private enum Status {
        CHECKING,
        UP_TO_DATE,
        UPDATE_AVAILABLE,
        DOWNLOADING,
        INSTALL_READY,
        UNSUPPORTED,
        FAILED
    }

    private record State(
        Status status,
        String currentVersion,
        String latestVersion,
        String message,
        UpdateInfo updateInfo
    ) {
        static State checking(String currentVersion) {
            return new State(Status.CHECKING, currentVersion, "", "Checking GitHub", null);
        }

        State withStatus(Status status, String message) {
            return new State(status, currentVersion, latestVersion, message, updateInfo);
        }
    }

    private record UpdateInfo(String version, String assetName, String downloadUrl) {
    }

    private record UpdateAsset(String name, String downloadUrl) {
    }
}
