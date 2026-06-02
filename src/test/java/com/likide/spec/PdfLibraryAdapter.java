package com.likide.spec;

/**
 * Adapter contract that bridges PRD tests to the concrete library API.
 *
 * Replace DefaultPdfLibraryAdapter with your own implementation and set:
 * -Da11y.pdf.adapter=com.likide.spec.YourAdapter
 */
public interface PdfLibraryAdapter {

    /** Builds a minimal valid PDF/UA candidate document. */
    byte[] buildValidMinimalDocument() throws Exception;

    /** Must throw when heading hierarchy is invalid (e.g., H1 -> H3). */
    void buildWithSkippedHeadingLevels() throws Exception;

    /** Must throw when an image has no alt text and is not decorative. */
    void buildWithImageMissingAltText() throws Exception;

    /** Builds a document that contains at least one list with L/LI/Lbl/LBody tags. */
    byte[] buildListDocument() throws Exception;

    /** Builds a document with page geometry/footer/header content tagged as Artifact. */
    byte[] buildDocumentWithPageArtifacts() throws Exception;
}
