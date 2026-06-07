package com.likide.a11y.pdf.model;

public record IntermediateToc(
        String title,
        int maxDepth,
        String itemMode,
        boolean showPageNumbers,
        SemanticMetadata semantic) implements IntermediateNode {
}
