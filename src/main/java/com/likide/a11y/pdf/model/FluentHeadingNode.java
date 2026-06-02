package com.likide.a11y.pdf.model;

public record FluentHeadingNode(int level, String text, IntermediateTextStyle style) implements FluentNode {
}
