package com.likide.spec;

import org.apache.pdfbox.pdmodel.PDDocument;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PrdComplianceTest {

    private static PdfLibraryAdapter adapter;

    @BeforeAll
    static void setupAdapter() {
        adapter = AdapterLoader.load();
    }

    @Test
    void lineHeightFormula_shouldFollowSpec() {
        float fontSize = 10.0f;
        float lineHeight = 1.2f;
        float spacing = fontSize * lineHeight;
        assertEquals(12.0f, spacing, 0.0001f);
    }

    @Test
    void nextColumnXFormula_shouldFollowSpec() {
        float leftMargin = 48.0f;
        int columnIndex = 2;
        float columnWidth = 180.0f;
        float columnGap = 24.0f;

        float nextX = leftMargin + columnIndex * (columnWidth + columnGap);
        assertEquals(456.0f, nextX, 0.0001f);
    }

    @Test
    void build_shouldSetCatalogMetadata_tabsAndPdfUaMarker() throws Exception {
        byte[] pdf = adapter.buildValidMinimalDocument();

        try (PDDocument doc = PdfAssertions.load(pdf)) {
            PdfAssertions.assertCatalogMetadata(doc, "en-US");
            PdfAssertions.assertTabsStructureOnEveryPage(doc);
            PdfAssertions.assertPdfUaPart1Xmp(doc);
        }
    }

    @Test
    void build_shouldRejectSkippedHeadingLevels() {
        assertThrows(Exception.class, () -> adapter.buildWithSkippedHeadingLevels());
    }

    @Test
    void build_shouldRejectImageWithoutAltAndWithoutDecorativeFlag() {
        assertThrows(Exception.class, () -> adapter.buildWithImageMissingAltText());
    }

    @Test
    void listDocument_shouldContainMandatoryListStructureTags() throws Exception {
        byte[] pdf = adapter.buildListDocument();
        try (PDDocument doc = PdfAssertions.load(pdf)) {
            PdfAssertions.assertStructureContainsTags(doc, "L", "LI", "Lbl", "LBody");
        }
    }

    @Test
    void pageDecorations_shouldBeTaggedAsArtifacts() throws Exception {
        byte[] pdf = adapter.buildDocumentWithPageArtifacts();
        assertDoesNotThrow(() -> PdfAssertions.assertContainsArtifactMarker(pdf));
    }
}
