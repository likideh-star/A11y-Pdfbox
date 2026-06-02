package com.likide.a11y.pdf.model;

public record IntermediateToc(
        String title,
        int maxDepth,
        SemanticMetadata semantic) implements IntermediateNode {
}
