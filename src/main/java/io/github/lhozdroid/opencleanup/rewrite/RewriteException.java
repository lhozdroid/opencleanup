package io.github.lhozdroid.opencleanup.rewrite;

/**
 * Signals a source parsing or rewriting failure.
 */
public class RewriteException extends Exception {

    public RewriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
