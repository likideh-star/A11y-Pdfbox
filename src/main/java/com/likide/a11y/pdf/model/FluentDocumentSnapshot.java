package com.likide.a11y.pdf.model;

import java.util.List;

/**
 * Neutral fluent-side snapshot consumed by the converter.
 */
public record FluentDocumentSnapshot(
        String lang,
        String title,
        boolean displayDocTitle,
        IntermediatePageSettings pageSettings,
        List<FluentNode> nodes) {
}
