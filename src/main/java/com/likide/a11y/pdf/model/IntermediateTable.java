package com.likide.a11y.pdf.model;

import java.util.List;

public record IntermediateTable(
        List<String> headerCells,
        List<IntermediateTableRow> rows,
        IntermediateBoxModel boxModel,
        SemanticMetadata semantic) implements IntermediateNode {
}
