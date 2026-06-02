package com.likide.a11y.pdf.model;

public record IntermediatePageSettings(
        int columns,
        float columnGap,
        float pageWidth,
        float pageHeight,
        float marginTop,
        float marginRight,
        float marginBottom,
        float marginLeft) {
}
