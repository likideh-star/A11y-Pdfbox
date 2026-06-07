package com.likide.a11y.pdf.model;

public final class DeclarativeToc implements DeclarativeNode {

    public String title;
    public Integer titleLevel;
    public Integer maxDepth;
    public String itemMode;
    public Boolean showPageNumbers;
    public DeclarativeSemanticMetadata semantic;
}
