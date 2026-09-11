package io.github.lhozdroid.opencleanup.rewrite;

/**
 * Signals a source parsing or rewriting failure.
 */
public class RewriteException extends Exception {

    /**
     * Creates a rewrite exception with a message and underlying cause.
     *
     * @param message the failure description
     * @param cause the underlying parsing or rewrite failure
     */
    public RewriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
