package org.one_cedrus.exception;

/**
 * Exception thrown when vault configuration is invalid
 */
public class ConfigurationException extends VaultException {
    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}