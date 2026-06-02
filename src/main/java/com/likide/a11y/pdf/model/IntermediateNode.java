package com.likide.a11y.pdf.model;

public sealed interface IntermediateNode permits IntermediateHeading, IntermediateParagraph, IntermediateFigure, IntermediateList {
    SemanticMetadata semantic();
}
