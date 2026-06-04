package com.likide.a11y.pdf.model;

public record IntermediateSection(
        int columns,
        float columnGap,
        SemanticMetadata semantic) implements IntermediateNode {
}
