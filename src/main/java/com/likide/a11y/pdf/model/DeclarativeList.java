package com.likide.a11y.pdf.model;

import java.util.ArrayList;
import java.util.List;

public final class DeclarativeList implements DeclarativeNode {

    public final List<String> items = new ArrayList<>();
    public final List<DeclarativeListItem> itemNodes = new ArrayList<>();
    public Boolean ordered;
    public Integer start;
    public String bulletStyle;
    public String customMarker;
    public DeclarativeTextStyle style;
    public String indentStyle;
    public Float customIndentPt;
    public DeclarativeBoxModel boxModel;
    public DeclarativeSemanticMetadata semantic;
}
