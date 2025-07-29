package org.one_cedrus.shared;

import com.google.gson.Gson;

import java.util.List;

public class ChangeLog implements Comparable<ChangeLog> {
    private long timestamp;
    // Only initial ChangeLog doesn't have parent
    private long parent;
    private Changes changes;

    public ChangeLog(long timestamp, Changes changes) {
        this.timestamp = timestamp;
        this.changes = changes;
    }

    public ChangeLog(long timestamp, Changes changes, long parent) {
        this.timestamp = timestamp;
        this.changes = changes;
        this.parent = parent;
    }

    public static class Changes {
        private List<FileChange> added;
        private List<FileChange> modified;
        private List<String> deleted;

        public Changes() {
        }

        public Changes(List<FileChange> added, List<FileChange> modified, List<String> deleted) {
            this.added = added;
            this.modified = modified;
            this.deleted = deleted;
        }

        // Getters and setters
        public List<FileChange> getAdded() {
            return added;
        }

        public void setAdded(List<FileChange> added) {
            this.added = added;
        }

        public List<FileChange> getModified() {
            return modified;
        }

        public void setModified(List<FileChange> modified) {
            this.modified = modified;
        }

        public List<String> getDeleted() {
            return deleted;
        }

        public void setDeleted(List<String> deleted) {
            this.deleted = deleted;
        }
    }

    public static ChangeLog fromJson(String json) {
        return new Gson().fromJson(json, ChangeLog.class);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Changes getChanges() {
        return changes;
    }

    public void setChanges(Changes changes) {
        this.changes = changes;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    public long getParent() {
        return parent;
    }

    public void setParent(long parent) {
        this.parent = parent;
    }

    @Override
    public int compareTo(ChangeLog other) {
        return Long.compare(this.timestamp, other.timestamp);
    }
}