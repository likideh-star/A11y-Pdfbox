package com.likide.a11y.pdf.model;

import java.util.List;

/**
 * Unified intermediate model produced by both fluent and declarative inputs.
 */
public record IntermediateDocument(
        String lang,
        String title,
        boolean displayDocTitle,
        IntermediatePageSettings pageSettings,
        List<IntermediateNode> nodes) {
}
