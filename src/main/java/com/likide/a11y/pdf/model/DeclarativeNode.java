package com.likide.a11y.pdf.model;

public sealed interface DeclarativeNode permits DeclarativeHeading, DeclarativeParagraph, DeclarativeFigure, DeclarativeList, DeclarativeTable, DeclarativeToc, DeclarativeCustomNode, DeclarativeSection {
}
