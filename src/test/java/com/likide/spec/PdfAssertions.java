package com.likide.spec;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PdfAssertions {

    private PdfAssertions() {
    }

    static PDDocument load(byte[] pdfBytes) throws IOException {
        return Loader.loadPDF(pdfBytes);
    }

    static void assertCatalogMetadata(PDDocument doc, String expectedLanguage) {
        assertNotNull(doc.getDocumentCatalog(), "Document catalog must exist");
        assertEquals(expectedLanguage, doc.getDocumentCatalog().getLanguage(),
                "Catalog /Lang must be set");
        assertNotNull(doc.getDocumentCatalog().getViewerPreferences(),
                "Viewer preferences must be present");
        assertTrue(Boolean.TRUE.equals(doc.getDocumentCatalog().getViewerPreferences().displayDocTitle()),
                "Viewer preferences must set /DisplayDocTitle true");
    }

    static void assertTabsStructureOnEveryPage(PDDocument doc) {
        for (PDPage page : doc.getPages()) {
            COSBase tabs = page.getCOSObject().getDictionaryObject(COSName.getPDFName("Tabs"));
            assertEquals(COSName.S, tabs, "Each page must define /Tabs /S");
        }
    }

    static void assertPdfUaPart1Xmp(PDDocument doc) throws IOException {
        assertNotNull(doc.getDocumentCatalog().getMetadata(), "XMP metadata stream must be present");
        String xmp = new String(doc.getDocumentCatalog().getMetadata().toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(xmp.contains("<pdfuaid:part>1</pdfuaid:part>") || xmp.contains("pdfuaid:part=\"1\""),
                "XMP must contain pdfUAid:part=1 marker");
    }

    static void assertStructureContainsTags(PDDocument doc, String... expectedTags) {
        PDStructureTreeRoot root = doc.getDocumentCatalog().getStructureTreeRoot();
        assertNotNull(root, "Structure tree root must exist");
        Set<String> tags = collectStructureTags(root.getCOSObject());
        for (String tag : expectedTags) {
            assertTrue(tags.contains(tag), "Expected structure tag not found: " + tag);
        }
    }

    static void assertContainsArtifactMarker(byte[] pdfBytes) throws IOException {
        try (PDDocument doc = load(pdfBytes)) {
            for (PDPage page : doc.getPages()) {
                if (page.getContents() == null) {
                    continue;
                }
                String content = new String(page.getContents().readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
                if (content.contains("/Artifact") || content.contains("BMC") || content.contains("BDC")) {
                    return;
                }
            }
        }
        throw new AssertionError("PDF stream must contain /Artifact marked content");
    }

    private static Set<String> collectStructureTags(COSBase node) {
        Set<String> tags = new HashSet<>();
        Set<COSBase> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        walk(node, tags, visited);
        return tags;
    }

    private static void walk(COSBase node, Set<String> outTags, Set<COSBase> visited) {
        if (node == null) {
            return;
        }

        if (!visited.add(node)) {
            return;
        }

        if (node instanceof COSObject cosObject) {
            walk(cosObject.getObject(), outTags, visited);
            return;
        }

        if (node instanceof COSDictionary dict) {
            COSName structureType = dict.getCOSName(COSName.S);
            if (structureType != null) {
                outTags.add(structureType.getName());
            }
            for (COSName key : dict.keySet()) {
                walk(dict.getDictionaryObject(key), outTags, visited);
            }
            return;
        }

        if (node instanceof COSArray array) {
            for (COSBase item : array) {
                walk(item, outTags, visited);
            }
        }
    }
}
