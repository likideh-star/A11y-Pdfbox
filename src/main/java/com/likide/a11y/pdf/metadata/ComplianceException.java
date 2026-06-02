package com.likide.a11y.pdf.metadata;

import com.likide.a11y.pdf.api.A11yPdfException;

/**
 * Signals PDF/UA compliance metadata issues.
 */
public class ComplianceException extends A11yPdfException {

    public ComplianceException(String message) {
        super(message);
    }
}
