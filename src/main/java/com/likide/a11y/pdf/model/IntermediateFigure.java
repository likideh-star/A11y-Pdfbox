package com.likide.a11y.pdf.model;

public record IntermediateFigure(
        String pathOrId,
        String altText,
        boolean decorative,
        SemanticMetadata semantic) implements IntermediateNode {
}
