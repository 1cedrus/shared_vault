package org.one_cedrus.manager;

import org.one_cedrus.communication.ApiClient;
import org.one_cedrus.communication.VWebSocketClient;
import org.one_cedrus.exception.ConfigurationException;
import org.one_cedrus.exception.SyncException;
import org.one_cedrus.exception.VaultNotInitializedException;
import org.one_cedrus.service.DirectoryStateService;
import org.one_cedrus.shared.ChangeLog;
import org.one_cedrus.shared.FileChange;
import org.one_cedrus.util.FileWatcher;
import org.one_cedrus.util.VaultConfig;

import sun.security.krb5.internal.LastReq;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;

public class VaultManager {
    private final Path linkedDirPath;
    private final String serverUrl;

    private final ApiClient apiClient;
    private final SharedVaultDirManager svDirManager;
    private final ChangeLogManager changeLogManager;
    private final DirectoryStateService directoryStateService;

    private VWebSocketClient wsClient;
    private FileWatcher watcher;

    private String vaultName;
    private VaultConfig vaultConfig;

    public VaultManager(Path linkedDirPath) throws Exception {
        this.svDirManager = new SharedVaultDirManager(linkedDirPath);
        this.vaultConfig = VaultConfig.loadFromFile(svDirManager.getConfigFilePath());
        this.serverUrl = vaultConfig.getServerUrl();
        this.linkedDirPath = linkedDirPath;
        this.changeLogManager = new ChangeLogManager(svDirManager);
        this.apiClient = new ApiClient(vaultConfig.getServerUrl());
        this.directoryStateService = new DirectoryStateService(svDirManager);
    }

    public VaultManager(Path linkedDirPath, String serverUrl) {
        this.linkedDirPath = linkedDirPath;
        this.serverUrl = serverUrl;
        this.apiClient = new ApiClient(serverUrl);
        this.svDirManager = new SharedVaultDirManager(linkedDirPath);
        this.changeLogManager = new ChangeLogManager(svDirManager);
        this.directoryStateService = new DirectoryStateService(svDirManager);
    }

    public String createVault(int debounceSeconds) throws SyncException, IOException {
        try {
            System.out.println("[INFO]: Scanning folder contents...");

            svDirManager.ensureSVDir();
            Map<String, Path> filesToUpload = svDirManager.scanLinkedDir();

            System.out.println("[INFO]: Found " + filesToUpload.size() + " files");

            ChangeLog initialCL = changeLogManager.createInitialChangeLog(filesToUpload);
            changeLogManager.saveChangeLog(initialCL);

            System.out.println("[INFO]: Uploading to server...");
            this.vaultName = apiClient.createVault(initialCL, filesToUpload);

            this.vaultConfig = new VaultConfig(vaultName, serverUrl, debounceSeconds);
            this.vaultConfig.setCurrentChangeLog(initialCL);

            vaultConfig.saveToFile(svDirManager.getConfigFilePath());

            return vaultName;
        } catch (IOException e) {
            throw new SyncException("Failed to create vault: " + e.getMessage(), e);
        }
    }

    public void monitorVault(String vaultName, int debounceSeconds) throws Exception {
        this.vaultName = vaultName;

        try {
            svDirManager.ensureSVDir();

            if (!svDirManager.getConfigFilePath().toFile().exists()) {
                this.vaultConfig = new VaultConfig(vaultName, serverUrl, debounceSeconds);
                vaultConfig.saveToFile(svDirManager.getConfigFilePath());
            } else {
                this.vaultConfig = VaultConfig.loadFromFile(svDirManager.getConfigFilePath());
                if (!this.vaultConfig.getVaultName().equals(vaultName)) {
                    throw new ConfigurationException("Vault name mismatch: expected " + this.vaultConfig.getVaultName() +
                            ", but got " + vaultName);
                }
            }

            syncFromServer();
        } catch (Exception e) {
            if (e instanceof ConfigurationException) {
                throw e;
            }
            throw new SyncException("Failed to monitor vault: " + e.getMessage(), e);
        }
    }

    public void startMonitoring() throws VaultNotInitializedException, IOException {
        if (vaultName == null || vaultConfig == null) {
            throw new VaultNotInitializedException("Vault not initialized. Call createVault() or monitorVault() first.");
        }

        try {
            System.out.println("[INFO]: Connecting to WebSocket...");

            URI webSocketURI = URI.create(vaultConfig.getWebsocketUrl());
            wsClient = new VWebSocketClient(
                    webSocketURI,
                    vaultName,
                    this::onRemoteChange,
                    code -> {
                        // TODO! Handle reconnect
                    });
            wsClient.connect();

            System.out.println("[INFO]: Starting file watcher...");
            watcher = new FileWatcher(linkedDirPath, this::onLocalChange, vaultConfig.getDebounceSeconds());
            watcher.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    watcher.stop();
                    wsClient.close();

                    System.out.println("[INFO]: Application closed");
                } catch (Exception ignored) {
                    // Ignore cleanup errors
                }
            }));

            System.out.println("[INFO]: Monitoring started. Press Ctrl+C to stop.");

            // Keep the main thread alive until shutdown
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Monitoring interrupted", e);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to start monitoring: " + e.getMessage(), e);
        }
    }

    private void onLocalChange(List<FileChange> fileChanges) {
        System.out.println("[DEBUG]: Local changes detected: " + fileChanges.size() + " file changes");
        for (FileChange fileChange : fileChanges) {
            System.out.println("[DEBUG]:   - " + fileChange.getChangeType() + ": " + fileChange.getPath());
        }

        ChangeLog changeLog = changeLogManager.createChangeLogFromFileChanges(fileChanges);

        System.out.println("[DEBUG]:   - Timestamp: " + changeLog.getTimestamp());
        System.out.println("[DEBUG]:   - Added: "
                + (changeLog.getChanges().getAdded() != null ? changeLog.getChanges().getAdded().size() : 0));
        System.out.println("[DEBUG]:   - Modified: "
                + (changeLog.getChanges().getModified() != null ? changeLog.getChanges().getModified().size() : 0));

        uploadAndSaveChanges(changeLog);
    }

    /**
     * Common method to upload changes to server and save them locally
     */
    private void uploadAndSaveChanges(ChangeLog changeLog) {
        Map<String, Path> filesToUpload = extractFilesFromChangeLog(List.of(changeLog));

        try {
            changeLogManager.saveChangeLog(changeLog);
            // TODO: Implement retry!
            apiClient.syncVault(vaultName, List.of(changeLog), filesToUpload);
            vaultConfig.setCurrentChangeLog(changeLog);
            vaultConfig.saveToFile(svDirManager.getConfigFilePath());
            System.out.println("[INFO]: Changes uploaded and saved successfully");
        } catch (IOException e) {
            System.err.println("[ERROR]: Failed to upload changes: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[ERROR]: Unexpected error during upload: " + e.getMessage());
        }
    }

    private Map<String, Path> extractFilesFromChangeLog(List<ChangeLog> changeLogs) {
        Map<String, Path> filesToUpload = new HashMap<>();
        for (ChangeLog changeLog : changeLogs) {
            if (changeLog.getChanges().getAdded() != null) {
                for (FileChange fileChange : changeLog.getChanges().getAdded()) {
                    Path filePath = linkedDirPath.resolve(fileChange.getPath());
                    if (filePath.toFile().exists()) {
                        filesToUpload.put(fileChange.getHash(), filePath);
                    }
                }
            }
            if (changeLog.getChanges().getModified() != null) {
                for (FileChange fileChange : changeLog.getChanges().getModified()) {
                    Path filePath = linkedDirPath.resolve(fileChange.getPath());
                    if (filePath.toFile().exists()) {
                        filesToUpload.put(fileChange.getHash(), filePath);
                    }
                }
            }
        }
        return filesToUpload;
    }

    private void onRemoteChange(String changedVaultName) {
        if (!changedVaultName.equals(vaultName)) {
            return;
        }

        try {
            watcher.pause();
            syncFromServer();
            watcher.start();
        } catch (SyncException e) {
            System.err.println("[ERROR]: Failed to sync from server: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("[ERROR]: IO error during sync: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[ERROR]: Unexpected error during remote sync: " + e.getMessage());
        }

        System.out.println("[INFO]: Remote changes detected in vault '" + changedVaultName + "'");
    }

    private boolean isDirChangedSinceLastSync() {
        Map<String, String> currentDirState = directoryStateService.getCurrentDirState();
        Map<String, String> afterDirState = directoryStateService
                .buildDirStateFromChangeLogs(findCurrentBranchChangeLogs());

        return !currentDirState.equals(afterDirState);
    }

    private void syncFromServer() throws Exception {
        boolean isDirChangedSinceLastSync = isDirChangedSinceLastSync();
        List<ChangeLog> newChangeLogs = fetchNewChangeLogsFromServer();
        List<ChangeLog> changeLogsToApply = determineChangeLogsToApply(newChangeLogs);

        handleUncommittedChanges(isDirChangedSinceLastSync, changeLogsToApply);

        System.out.println("[INFO]: Sync completed");
        vaultConfig.saveToFile(svDirManager.getConfigFilePath());
    }

    private List<ChangeLog> fetchNewChangeLogsFromServer() throws Exception {
        Long newestLocalTimestamp = changeLogManager.getNewestLocalTimestamp();

        System.out.println("[INFO]: Fetching change logs from server since " +
                (newestLocalTimestamp != null ? newestLocalTimestamp : "beginning"));

        List<ChangeLog> newChangeLogs;
        if (newestLocalTimestamp != null) {
            newChangeLogs = apiClient.getChangeLogsSince(vaultName, newestLocalTimestamp);
        } else {
            newChangeLogs = apiClient.getChangeLogs(vaultName);
        }

        System.out.println("[INFO]: Found " + newChangeLogs.size() + " new change logs from server");

        // Save all new change logs locally
        for (ChangeLog changeLog : newChangeLogs) {
            changeLogManager.saveChangeLog(changeLog);
        }

        return newChangeLogs;
    }

    private List<ChangeLog> determineChangeLogsToApply(List<ChangeLog> newChangeLogs) {
        List<ChangeLog> sortedChangeLogs = newChangeLogs.stream().sorted().toList();
        List<ChangeLog> toApply = new ArrayList<>();

        ChangeLog latestChangeLog = vaultConfig.getCurrentChangeLog();

        // Means this is the first sync or no change logs were found, 
        // also there might be no chance that sortedChangeLogs is empty.
        // But it's better to be safe.
        if (latestChangeLog == null) {
            latestChangeLog = sortedChangeLogs.isEmpty() ? null : sortedChangeLogs.get(0); 
        } 

        if (latestChangeLog != null) {
          for (ChangeLog changeLog : sortedChangeLogs) {
                if (changeLog.getParent() == latestChangeLog.getTimestamp()) {
                    toApply.add(changeLog);
                }

                latestChangeLog = changeLog;
            }

          vaultConfig.setCurrentChangeLog(latestChangeLog);
        }

        // If this step is break, the vault config will be wrongly saved
        // Might lead to unexpected behavior
        try {
            vaultConfig.saveToFile(svDirManager.getConfigFilePath());
        } catch (IOException e) {
            System.err.println("[ERROR]: Failed to save vault config: " + e.getMessage());
        }

        return toApply;
    }

    private void handleUncommittedChanges(boolean isDirChangedSinceLastSync, List<ChangeLog> changeLogsToApply)
            throws Exception {

        if (isDirChangedSinceLastSync) {
            System.out.println("[INFO]: Local directory has uncommitted changes since last sync");
        } else {
            System.out.println("[INFO]: No local uncommitted changes detected");
        }

        if (isDirChangedSinceLastSync) {
            handleLocalUncommittedChanges();
        } else {
            // Apply remote changes to local directory
            for (ChangeLog changeLog : changeLogsToApply) {
                applyChangeLog(changeLog);
            }
        }
    }

    private void handleLocalUncommittedChanges() {
        Map<String, String> afterDirState = directoryStateService
                .buildDirStateFromChangeLogs(findCurrentBranchChangeLogs());
        Map<String, String> currentDirState = directoryStateService.getCurrentDirState();

        if (!currentDirState.equals(afterDirState)) {
            List<FileChange> uncommittedChanges = detectUncommittedChanges(currentDirState, afterDirState);
            ChangeLog changeLog = changeLogManager.createChangeLogFromFileChanges(uncommittedChanges);
            uploadAndSaveChanges(changeLog);
        }
    }

    private List<FileChange> detectUncommittedChanges(Map<String, String> currentDirState,
            Map<String, String> afterDirState) {
        List<FileChange> notCommittedChanges = new ArrayList<>();

        // Detect added and modified files
        for (Map.Entry<String, String> entry : currentDirState.entrySet()) {
            String relPath = entry.getKey();
            String hash = entry.getValue();

            if (!afterDirState.containsKey(relPath)) {
                notCommittedChanges.add(new FileChange(relPath, hash, "ADDED"));
            } else if (!afterDirState.get(relPath).equals(hash)) {
                notCommittedChanges.add(new FileChange(relPath, hash, "MODIFIED"));
            }
        }

        // Detect deleted files
        for (Map.Entry<String, String> entry : afterDirState.entrySet()) {
            String relPath = entry.getKey();

            if (!currentDirState.containsKey(relPath)) {
                notCommittedChanges.add(new FileChange(relPath, entry.getValue(), "DELETED"));
            }
        }

        return notCommittedChanges;
    }

    private void applyChangeLog(ChangeLog changeLog) throws Exception {
        long timestamp = changeLog.getTimestamp();
        ChangeLog.Changes changes = changeLog.getChanges();

        System.out.println("[DEBUG]: Applying change log " + timestamp);

        // Handle added files
        if (changes.getAdded() != null) {
            for (FileChange fileChange : changes.getAdded()) {
                Path filePath = linkedDirPath.resolve(fileChange.getPath());
                String fileHash = fileChange.getHash();

                // Download file if we don't have it locally
                if (!svDirManager.hasFileByHash(fileHash)) {
                    System.out.println("[INFO]: Downloading file: " + fileChange.getPath());
                    byte[] fileContent = apiClient.getFile(vaultName, fileHash);
                    svDirManager.saveFileByHash(fileHash, fileContent);
                }

                // Copy to local folder
                System.out.println("[INFO]: Creating: " + fileChange.getPath());
                svDirManager.restoreFileFromHash(fileHash, filePath);
            }
        }

        // Handle modified files
        if (changes.getModified() != null) {
            for (FileChange fileChange : changes.getModified()) {
                Path filePath = linkedDirPath.resolve(fileChange.getPath());
                String fileHash = fileChange.getHash();

                // Download file if we don't have it locally
                if (!svDirManager.hasFileByHash(fileHash)) {
                    System.out.println("[INFO]: Downloading file: " + fileChange.getPath());
                    byte[] fileContent = apiClient.getFile(vaultName, fileHash);
                    svDirManager.saveFileByHash(fileHash, fileContent);
                }

                // Update local folder
                System.out.println("[INFO]: Updating: " + fileChange.getPath());
                svDirManager.restoreFileFromHash(fileHash, filePath);
            }
        }

        // Handle deleted files
        if (changes.getDeleted() != null) {
            for (String filePathStr : changes.getDeleted()) {
                Path filePath = linkedDirPath.resolve(filePathStr);
                if (filePath.toFile().exists()) {
                    System.out.println("[INFO]: Deleting: " + filePathStr);
                    filePath.toFile().delete();
                }
            }
        }
    }

    public String getVaultName() {
        try {
            VaultConfig existingConfig = VaultConfig.loadFromFile(svDirManager.getConfigFilePath());
            return existingConfig.getVaultName();
        } catch (IOException exception) {
            return null;
        }
    }

    private List<ChangeLog> findCurrentBranchChangeLogs() {
        List<ChangeLog> changeLogs = changeLogManager.getLocalChangeLogs();
        if (changeLogs.isEmpty()) {
            return Collections.emptyList();
        }

        List<ChangeLog> currentBranch = new ArrayList<>();
        ChangeLog tmp = vaultConfig.getCurrentChangeLog();

        currentBranch.add(tmp);
        for (ChangeLog cl : changeLogs) {
            if (tmp.getParent() == cl.getTimestamp()) {
                currentBranch.add(cl);
                tmp = cl;
            }
        }

        return currentBranch.stream().sorted().toList();
    }

}
