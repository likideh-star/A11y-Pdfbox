package com.likide.a11y.pdf.model;

import java.util.List;

public record IntermediateList(
        List<String> items,
        IntermediateBoxModel boxModel,
                IntermediateTextStyle style,
        SemanticMetadata semantic) implements IntermediateNode {

        public IntermediateList(List<String> items, IntermediateBoxModel boxModel, SemanticMetadata semantic) {
                this(items, boxModel, null, semantic);
        }
}
