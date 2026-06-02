package com.likide.a11y.pdf.model;

public record SemanticMetadata(String structureTag, String roleHint, String nodeFamily) {

	public SemanticMetadata(String structureTag) {
		this(structureTag, null, null);
	}
}
