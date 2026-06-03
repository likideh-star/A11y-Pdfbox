package com.likide.a11y.pdf.model;

import java.util.List;

public record FluentTableNode(List<String> headerCells, List<IntermediateTableRow> rows) implements FluentNode {
}
