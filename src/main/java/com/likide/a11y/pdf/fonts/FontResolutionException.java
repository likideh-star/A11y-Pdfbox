package com.likide.a11y.pdf.fonts;

import com.likide.a11y.pdf.api.A11yPdfException;

/**
 * Signals failures while resolving document font resources.
 */
public class FontResolutionException extends A11yPdfException {

    public FontResolutionException(String message) {
        super(message);
    }
}
