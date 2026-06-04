package com.likide.a11y.pdf.model;

public record FluentFigureNode(String pathOrId, String altText, boolean decorative, String flowMode) implements FluentNode {
}
