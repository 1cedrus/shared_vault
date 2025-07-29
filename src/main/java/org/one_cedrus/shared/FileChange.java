package org.one_cedrus.shared;

public class FileChange {
    private String path;
    private String hash;
    private String changeType; // "ADDED", "MODIFIED", "DELETED"

    public FileChange() {
    }

    public FileChange(String path, String hash) {
        this.path = path;
        this.hash = hash;
        this.changeType = "ADDED"; // Default for backward compatibility
    }

    public FileChange(String path, String hash, String changeType) {
        this.path = path;
        this.hash = hash;
        this.changeType = changeType;
    }

    // Getters and setters
    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    @Override
    public String toString() {
        return changeType + ": " + path;
    }
}