package com.likide.a11y.pdf.model;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DeclarativeCustomNode implements DeclarativeNode {

    public String family;
    public String type;
    public final Map<String, String> attributes = new LinkedHashMap<>();
    public DeclarativeSemanticMetadata semantic;
}
