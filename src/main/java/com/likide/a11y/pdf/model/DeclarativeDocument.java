package com.likide.a11y.pdf.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Declarative API root object intended for JSON/YAML-style population.
 */
public final class DeclarativeDocument {

    public String lang;
    public String title;
    public Boolean displayDocTitle;
    public DeclarativePageSettings page;
    /**
     * Optional font family definitions keyed by family name.
     * Use key {@code "default"} to override the document default font.
     * Each value specifies paths to TrueType font files for each variant.
     */
    public final Map<String, DeclarativeFontConfig> fonts = new LinkedHashMap<>();
    public DeclarativePageChrome pageChrome;
    public final List<DeclarativeNode> nodes = new ArrayList<>();
}
