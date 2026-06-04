package com.likide.a11y.pdf.model;

import java.util.List;

public record FluentListNode(
	List<FluentListItemNode> itemNodes,
	boolean ordered,
	int start,
	String bulletStyle,
	String customMarker) implements FluentNode {
}
