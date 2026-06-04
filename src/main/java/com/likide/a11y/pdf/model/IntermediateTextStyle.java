package com.likide.a11y.pdf.model;

public record IntermediateTextStyle(
        float lineHeightMultiplier,
                IntermediateBoxModel boxModel,
                String fontFamily,
                String fontVariant) {

        public IntermediateTextStyle(float lineHeightMultiplier, IntermediateBoxModel boxModel) {
                this(lineHeightMultiplier, boxModel, null, null);
        }
}
