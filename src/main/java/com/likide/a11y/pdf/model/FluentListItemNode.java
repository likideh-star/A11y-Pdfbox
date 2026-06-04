package com.likide.a11y.pdf.model;

public record FluentListItemNode(
        String text,
        FluentListNode nestedList) {
}
