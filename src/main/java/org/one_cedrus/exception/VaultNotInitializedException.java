package org.one_cedrus.exception;

/**
 * Exception thrown when vault is not properly initialized
 */
public class VaultNotInitializedException extends VaultException {
    public VaultNotInitializedException(String message) {
        super(message);
    }
}