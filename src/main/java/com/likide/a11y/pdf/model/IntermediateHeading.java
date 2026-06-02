package com.likide.a11y.pdf.model;

public record IntermediateHeading(
        int level,
        String text,
        IntermediateTextStyle style,
        SemanticMetadata semantic) implements IntermediateNode {
}
