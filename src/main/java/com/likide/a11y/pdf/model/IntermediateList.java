package com.likide.a11y.pdf.model;

import java.util.List;

public record IntermediateList(
        List<IntermediateListItem> itemNodes,
        IntermediateBoxModel boxModel,
        IntermediateTextStyle style,
        String indentStyle,
        Float customIndentPt,
        Boolean ordered,
        Integer start,
        String bulletStyle,
        String customMarker,
        SemanticMetadata semantic) implements IntermediateNode {

        public IntermediateList(List<IntermediateListItem> itemNodes, IntermediateBoxModel boxModel, SemanticMetadata semantic) {
                this(itemNodes, boxModel, null, null, null, null, null, null, null, semantic);
        }
}
