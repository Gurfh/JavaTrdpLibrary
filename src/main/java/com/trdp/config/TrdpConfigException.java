package com.trdp.config;

/**
 * Checked exception thrown when loading or validating a TRDP XML configuration fails.
 *
 * @see TrdpConfig#load(java.nio.file.Path)
 */
public class TrdpConfigException extends Exception {

    /**
     * Creates an exception with the given message.
     *
     * @param message the detail message.
     */
    public TrdpConfigException(String message) {
        super(message);
    }

    /**
     * Creates an exception with the given message and cause.
     *
     * @param message the detail message.
     * @param cause   the underlying cause.
     */
    public TrdpConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
