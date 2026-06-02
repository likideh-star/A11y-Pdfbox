package com.likide.a11y.pdf.model;

import java.util.ArrayList;
import java.util.List;

public final class DeclarativeTable implements DeclarativeNode {

    public final List<String> headerCells = new ArrayList<>();
    public final List<DeclarativeTableRow> rows = new ArrayList<>();
    public DeclarativeSemanticMetadata semantic;
}
