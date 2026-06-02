package com.likide.a11y.pdf.model;

import java.util.Map;

public record IntermediateCustomNode(
        String family,
        String type,
        Map<String, String> attributes,
        SemanticMetadata semantic) implements IntermediateNode {
}
