package com.likide.a11y.pdf.model;

import java.util.List;

public record IntermediateList(
        List<String> items,
        IntermediateBoxModel boxModel,
                IntermediateTextStyle style,
                String indentStyle,
                Float customIndentPt,
        SemanticMetadata semantic) implements IntermediateNode {

        public IntermediateList(List<String> items, IntermediateBoxModel boxModel, SemanticMetadata semantic) {
                this(items, boxModel, null, null, null, semantic);
        }
}
