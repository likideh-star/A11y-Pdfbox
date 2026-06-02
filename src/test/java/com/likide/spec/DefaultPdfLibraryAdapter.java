package com.likide.spec;

/**
 * Default adapter intentionally unimplemented.
 *
 * Create your own adapter and set JVM property:
 * -Da11y.pdf.adapter=com.likide.spec.YourAdapter
 */
public class DefaultPdfLibraryAdapter implements PdfLibraryAdapter {

    private UnsupportedOperationException notImplemented() {
        return new UnsupportedOperationException(
                "Provide a concrete adapter via -Da11y.pdf.adapter=<fqcn> that implements PdfLibraryAdapter");
    }

    @Override
    public byte[] buildValidMinimalDocument() {
        throw notImplemented();
    }

    @Override
    public void buildWithSkippedHeadingLevels() {
        throw notImplemented();
    }

    @Override
    public void buildWithImageMissingAltText() {
        throw notImplemented();
    }

    @Override
    public byte[] buildListDocument() {
        throw notImplemented();
    }

    @Override
    public byte[] buildDocumentWithPageArtifacts() {
        throw notImplemented();
    }
}
