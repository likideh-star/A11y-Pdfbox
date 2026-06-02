package com.likide.a11y.pdf.validation;

import com.likide.a11y.pdf.api.A11yPdfException;

/**
 * Signals invalid document input or layout constraints.
 */
public class ValidationException extends A11yPdfException {

    public ValidationException(String message) {
        super(message);
    }
}
