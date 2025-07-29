package org.one_cedrus.shared;

public enum MessageType {
    HELLO, // Client introduces itself and lists monitored vaults
    FILE_CHANGE, // Server notifies client about changes in vault
    HEARTBEAT, // Keep connection alive
    STATUS // Client status update (e.g., currently processing changes)
}
