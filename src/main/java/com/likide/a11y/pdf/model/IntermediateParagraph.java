package com.likide.a11y.pdf.model;

public record IntermediateParagraph(
        String text,
        IntermediateTextStyle style,
        SemanticMetadata semantic) implements IntermediateNode {
}
