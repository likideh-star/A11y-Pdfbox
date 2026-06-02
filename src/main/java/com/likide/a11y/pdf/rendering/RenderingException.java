package com.likide.a11y.pdf.rendering;

import com.likide.a11y.pdf.api.A11yPdfException;

/**
 * Signals failures while writing PDF binary output.
 */
public class RenderingException extends A11yPdfException {

    public RenderingException(String message, Throwable cause) {
        super(message, cause);
    }
}
