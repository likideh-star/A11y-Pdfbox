package com.likide.a11y.pdf.model;

import java.util.ArrayList;
import java.util.List;

public final class DeclarativeList implements DeclarativeNode {

    public final List<String> items = new ArrayList<>();
    public DeclarativeSemanticMetadata semantic;
}
