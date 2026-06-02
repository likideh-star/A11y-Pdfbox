package com.likide.a11y.pdf.api;

/**
 * Base runtime exception for all library-level failures.
 */
public class A11yPdfException extends RuntimeException {

    public A11yPdfException(String message) {
        super(message);
    }

    public A11yPdfException(String message, Throwable cause) {
        super(message, cause);
    }
}
