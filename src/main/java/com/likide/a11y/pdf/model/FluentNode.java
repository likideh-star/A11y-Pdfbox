package com.likide.a11y.pdf.model;

public sealed interface FluentNode permits FluentHeadingNode, FluentParagraphNode, FluentFigureNode, FluentListNode, FluentTableNode, FluentTocNode, FluentCustomNode, FluentSectionNode {
}
