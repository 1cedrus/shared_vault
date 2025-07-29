package org.one_cedrus.exception;

/**
 * Exception thrown when vault sync operations fail
 */
public class SyncException extends VaultException {
    public SyncException(String message) {
        super(message);
    }

    public SyncException(String message, Throwable cause) {
        super(message, cause);
    }
}