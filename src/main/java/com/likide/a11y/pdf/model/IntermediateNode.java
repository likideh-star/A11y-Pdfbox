package com.likide.a11y.pdf.model;

public sealed interface IntermediateNode permits IntermediateHeading, IntermediateParagraph, IntermediateFigure, IntermediateList, IntermediateTable, IntermediateToc, IntermediateCustomNode, IntermediateSection {
    SemanticMetadata semantic();
}
