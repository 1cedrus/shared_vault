package org.one_cedrus.exception;

/**
 * Base exception for all vault-related operations
 */
public class VaultException extends Exception {
    public VaultException(String message) {
        super(message);
    }

    public VaultException(String message, Throwable cause) {
        super(message, cause);
    }
}