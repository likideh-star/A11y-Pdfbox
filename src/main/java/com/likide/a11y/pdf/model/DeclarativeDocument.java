package com.likide.a11y.pdf.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Declarative API root object intended for JSON/YAML-style population.
 */
public final class DeclarativeDocument {

    public String lang;
    public String title;
    public Boolean displayDocTitle;
    public DeclarativePageSettings page;
    public final List<DeclarativeNode> nodes = new ArrayList<>();
}
