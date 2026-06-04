package com.likide.a11y.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkedContentReference;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.junit.jupiter.api.Test;

class A11yPdfDocumentMcidTest {

    @Test
    void buildBytes_textOnly_shouldBindLayoutBlocksToMcidStructureElements() throws IOException {
        byte[] pdf = A11yPdfDocument.builder()
                .pageSize(220.0f, 140.0f)
                .pageMargin(20.0f)
                .heading(1, "Heading")
                .paragraph("First paragraph")
                .paragraph("Second paragraph")
                .buildBytes();

        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDStructureTreeRoot structureTreeRoot = document.getDocumentCatalog().getStructureTreeRoot();
            assertNotNull(structureTreeRoot);
            assertEquals(3, structureTreeRoot.getKids().size());

            for (int i = 0; i < structureTreeRoot.getKids().size(); i++) {
                Object kid = structureTreeRoot.getKids().get(i);
                assertTrue(kid instanceof PDStructureElement);
                PDStructureElement element = (PDStructureElement) kid;
                assertEquals(1, element.getKids().size());
                assertTrue(element.getKids().get(0) instanceof PDMarkedContentReference);
                PDMarkedContentReference mcr = (PDMarkedContentReference) element.getKids().get(0);
                assertEquals(i, mcr.getMCID());
            }

            String allPageContent = readAllPageContent(document);
            assertTrue(allPageContent.contains("/MCID 0"));
            assertTrue(allPageContent.contains("/MCID 1"));
            assertTrue(allPageContent.contains("/MCID 2"));
        }
    }

    private String readAllPageContent(PDDocument document) throws IOException {
        StringBuilder out = new StringBuilder();
        for (PDPage page : document.getPages()) {
            try (InputStream in = page.getContents()) {
                out.append(new String(in.readAllBytes(), StandardCharsets.ISO_8859_1));
            }
        }
        return out.toString();
    }
}
