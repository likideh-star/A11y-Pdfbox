package com.likide.a11y.pdf.model;

import java.util.List;

public record FluentListNode(List<String> items, boolean ordered, int start) implements FluentNode {
}
