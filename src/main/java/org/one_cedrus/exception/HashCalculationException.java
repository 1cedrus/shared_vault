package org.one_cedrus.exception;

/**
 * Exception thrown when hash calculation fails
 */
public class HashCalculationException extends VaultException {
    public HashCalculationException(String message, Throwable cause) {
        super(message, cause);
    }
}