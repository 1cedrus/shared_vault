package org.one_cedrus.manager;

import org.one_cedrus.exception.HashCalculationException;
import org.one_cedrus.util.HashCalculator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SharedVaultDirManager {
    public static String SHARED_VAULT_REGISTRY_DIRNAME = ".sv";
    public static String FILES_DIRNAME = "files";
    public static String CHANGE_LOGS_DIRNAME = "change_logs";
    public static String CONFIG_FILE_NAME = "config.json";

    private final Path linkedDir;
    private final Path svDir;
    private final Path filesDir;
    private final Path changeLogsDir;
    private final Path configFile;

    public SharedVaultDirManager(Path linkedDir) {
        this.linkedDir = linkedDir;
        this.svDir = linkedDir.resolve(SHARED_VAULT_REGISTRY_DIRNAME);
        this.filesDir = svDir.resolve(FILES_DIRNAME);
        this.changeLogsDir = svDir.resolve(CHANGE_LOGS_DIRNAME);
        this.configFile = svDir.resolve(CONFIG_FILE_NAME);
    }

    public void ensureSVDir() throws IOException {
        Files.createDirectories(svDir);
        Files.createDirectories(filesDir);
        Files.createDirectories(changeLogsDir);
    }

    public String calculateFileHash(Path filePath) throws IOException, HashCalculationException {
        return HashCalculator.calculateFileHash(filePath.toFile());
    }

    public Map<String, Path> scanLinkedDir() {
        File dir = linkedDir.toFile();

        if (!dir.exists() || !dir.isDirectory() || dir.listFiles() == null) {
            return Map.of();
        }

        Map<String, Path> files = new HashMap<>();

        Arrays.stream(Objects.requireNonNull(dir.listFiles()))
                .filter(f -> Files.isRegularFile(f.toPath()) && !isIgnoredPath(f.toPath()))
                .forEach(f -> {
                    try {
                        String fileHash = calculateFileHash(f.toPath());
                        files.put(fileHash, f.toPath());
                    } catch (IOException | HashCalculationException e) {
                        System.out.println(
                                "[ERROR]: Failed to calculate hash for file " + f.getPath() + ": " + e.getMessage());
                    }
                });

        return files;
    }

    public void saveFileByHash(String fileHash, byte[] content) throws IOException {
        Path hashFilePath = filesDir.resolve(fileHash);
        Files.write(hashFilePath, content);
    }

    public boolean hasFileByHash(String fileHash) {
        return Files.exists(filesDir.resolve(fileHash));
    }

    public void restoreFileFromHash(String fileHash, Path targetPath) throws IOException {
        Path hashFilePath = filesDir.resolve(fileHash);

        if (!Files.exists(hashFilePath)) {
            throw new IOException("File with hash " + fileHash + " not found in local storage");
        }

        Files.copy(hashFilePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
    }

    private static boolean isIgnoredPath(Path path) {
        String pathStr = path.toAbsolutePath().toString();

        return pathStr.contains(".sv") ||
                pathStr.contains(".git") ||
                pathStr.contains(".DS_Store");
    }

    public String getRelativePath(Path path) {
        Path relativePath = linkedDir.relativize(path);
        return relativePath.toString().replace("\\", "/");
    }

    public Path getConfigFilePath() {
        return configFile;
    }

    public Path getChangeLogFilePath() {
        return changeLogsDir;
    }
}