package com.likide.spec;

import org.apache.pdfbox.pdmodel.PDDocument;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.likide.a11y.pdf.A11yPdfDocument;

class BaselineSmokeTest {

    @Test
    void builder_shouldEmitTaggedPdfCandidateWithoutAdapterWiring() throws Exception {
        byte[] pdf = A11yPdfDocument.builder()
                .lang("en-US")
                .title("Milestone 1 Smoke")
                .displayDocTitle(true)
                .heading(1, "Smoke heading")
                .paragraph("Smoke paragraph")
                .buildBytes();

        try (PDDocument doc = PdfAssertions.load(pdf)) {
            PdfAssertions.assertCatalogMetadata(doc, "en-US");
            PdfAssertions.assertTabsStructureOnEveryPage(doc);
            PdfAssertions.assertPdfUaPart1Xmp(doc);
            PdfAssertions.assertStructureContainsTags(doc, "H1", "P");
            assertEquals(1, doc.getNumberOfPages());
        }
    }
}
