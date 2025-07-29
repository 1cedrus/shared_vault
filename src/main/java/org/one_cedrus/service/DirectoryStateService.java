package org.one_cedrus.service;

import org.one_cedrus.manager.SharedVaultDirManager;
import org.one_cedrus.shared.ChangeLog;
import org.one_cedrus.shared.FileChange;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for managing directory state operations
 */
public class DirectoryStateService {
    private final SharedVaultDirManager svDirManager;

    public DirectoryStateService(SharedVaultDirManager svDirManager) {
        this.svDirManager = svDirManager;
    }

    /**
     * Build directory state from a sequence of change logs
     */
    public Map<String, String> buildDirStateFromChangeLogs(List<ChangeLog> changeLogs) {
        Map<String, String> dirState = new HashMap<>();

        changeLogs.forEach(cl -> {
            ChangeLog.Changes changes = cl.getChanges();

            if (changes.getAdded() != null) {
                for (FileChange fc : changes.getAdded()) {
                    dirState.put(fc.getPath(), fc.getHash());
                }
            }
            if (changes.getModified() != null) {
                for (FileChange fc : changes.getModified()) {
                    dirState.put(fc.getPath(), fc.getHash());
                }
            }
            if (changes.getDeleted() != null) {
                for (String delPath : changes.getDeleted()) {
                    dirState.remove(delPath);
                }
            }

            System.out.println("[DEBUG]: " + cl.getTimestamp());
        });

        return dirState;
    }

    /**
     * Get current directory state by scanning local files
     */
    public Map<String, String> getCurrentDirState() {
        Map<String, Path> localFilesByHash = svDirManager.scanLinkedDir();
        Map<String, String> localFileState = new HashMap<>();

        for (Map.Entry<String, Path> entry : localFilesByHash.entrySet()) {
            String hash = entry.getKey();
            String relPath = svDirManager.getRelativePath(entry.getValue());
            localFileState.put(relPath, hash);
        }

        return localFileState;
    }
}