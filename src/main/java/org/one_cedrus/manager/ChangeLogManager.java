package org.one_cedrus.manager;

import org.one_cedrus.shared.ChangeLog;
import org.one_cedrus.shared.FileChange;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public class ChangeLogManager {
    private final SharedVaultDirManager svDirManager;

    public ChangeLogManager(SharedVaultDirManager svDirManager) {
        this.svDirManager = svDirManager;
    }

    public ChangeLog createInitialChangeLog(Map<String, Path> files) {
        List<FileChange> addedFiles = new ArrayList<>();

        for (Map.Entry<String, Path> entry : files.entrySet()) {
            String hash = entry.getKey();
            Path filePath = entry.getValue();
            String relativePath = svDirManager.getRelativePath(filePath);
            addedFiles.add(new FileChange(relativePath, hash, "ADDED"));
        }

        ChangeLog.Changes changes = new ChangeLog.Changes();
        changes.setAdded(addedFiles);
        changes.setModified(new ArrayList<>());
        changes.setDeleted(new ArrayList<>());
        return new ChangeLog(System.currentTimeMillis(), changes);
    }

    public Long getNewestLocalTimestamp() {
        TreeSet<Long> timestamps = getLocalTimestamps();
        return timestamps.isEmpty() ? null : timestamps.last();
    }

    public ChangeLog createChangeLogFromFileChanges(List<FileChange> changes) {
        if (changes.isEmpty()) {
            return new ChangeLog(System.currentTimeMillis(), null);
        }

        // Group file changes by type
        List<FileChange> addedFiles = new ArrayList<>();
        List<FileChange> modifiedFiles = new ArrayList<>();
        List<String> deletedFiles = new ArrayList<>();

        for (FileChange change : changes) {
            switch (change.getChangeType()) {
                case "ADDED":
                    addedFiles.add(change);
                    break;
                case "MODIFIED":
                    modifiedFiles.add(change);
                    break;
                case "DELETED":
                    deletedFiles.add(change.getPath());
                    break;
            }
        }

        ChangeLog.Changes changeLogChanges = new ChangeLog.Changes();
        changeLogChanges.setAdded(addedFiles.isEmpty() ? null : addedFiles);
        changeLogChanges.setModified(modifiedFiles.isEmpty() ? null : modifiedFiles);
        changeLogChanges.setDeleted(deletedFiles.isEmpty() ? null : deletedFiles);

        if (hasChanges(changeLogChanges)) {
            Long newestTimestamp = getNewestLocalTimestamp();
            return new ChangeLog(System.currentTimeMillis(), changeLogChanges,
                    newestTimestamp != null ? newestTimestamp : 0);
        }

        return new ChangeLog(System.currentTimeMillis(), null);
    }

    private boolean hasChanges(ChangeLog.Changes changes) {
        return (changes.getAdded() != null && !changes.getAdded().isEmpty()) ||
                (changes.getModified() != null && !changes.getModified().isEmpty()) ||
                (changes.getDeleted() != null && !changes.getDeleted().isEmpty());
    }

    public void saveChangeLog(ChangeLog changeLog) throws IOException {
        Files.createDirectories(svDirManager.getChangeLogFilePath());

        String fileName = String.format("%015d.json", changeLog.getTimestamp());
        if (Files.exists(svDirManager.getChangeLogFilePath().resolve(fileName)))
            return;

        Path changeLogFile = svDirManager.getChangeLogFilePath().resolve(fileName);
        Files.writeString(changeLogFile, changeLog.toJson());
    }

    public TreeSet<Long> getLocalTimestamps() {
        TreeSet<Long> timestamps = new TreeSet<>();

        if (!Files.exists(svDirManager.getChangeLogFilePath())) {
            return timestamps;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(svDirManager.getChangeLogFilePath(), "*.json")) {
            for (Path path : stream) {
                String fileName = path.getFileName().toString();
                try {
                    String timestampStr = fileName.substring(0, fileName.length() - 5);
                    long timestamp = Long.parseLong(timestampStr);
                    timestamps.add(timestamp);
                } catch (NumberFormatException e) {
                    System.err.println("[ERROR]: Invalid change log filename: " + fileName);
                }
            }
        } catch (IOException e) {
            System.err.println("[ERROR]: Error reading change log directory: " + e.getMessage());
        }

        return timestamps;
    }

    public List<ChangeLog> getLocalChangeLogs() {
        List<ChangeLog> changeLogs = new ArrayList<>();
        TreeSet<Long> timestamps = getLocalTimestamps();

        for (Long timestamp : timestamps) {
            String fileName = String.format("%015d.json", timestamp);
            Path changeLogFile = svDirManager.getChangeLogFilePath().resolve(fileName);

            if (Files.exists(changeLogFile)) {
                try {
                    String jsonContent = Files.readString(changeLogFile);
                    ChangeLog changeLog = ChangeLog.fromJson(jsonContent);
                    changeLogs.add(changeLog);
                } catch (IOException e) {
                    System.err.println("[ERROR]: Error reading change log file: " + e.getMessage());
                }
            }
        }

        return changeLogs;
    }
}