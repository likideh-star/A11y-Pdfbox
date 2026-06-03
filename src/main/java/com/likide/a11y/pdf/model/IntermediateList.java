package com.likide.a11y.pdf.model;

import java.util.List;

public record IntermediateList(
        List<String> items,
        IntermediateBoxModel boxModel,
        SemanticMetadata semantic) implements IntermediateNode {
}
