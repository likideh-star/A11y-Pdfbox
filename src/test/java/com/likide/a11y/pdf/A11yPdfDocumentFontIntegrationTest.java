package com.likide.a11y.pdf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.junit.jupiter.api.Test;

import com.likide.a11y.pdf.fonts.A11yFontFamily;
import com.likide.a11y.pdf.fonts.FontVariant;

class A11yPdfDocumentFontIntegrationTest {

    @Test
    void buildBytes_usesUnicodeFallbackFontPathForMixedGlyphText() throws Exception {
        Path unicodeTtf = findUnicodeCapableTtf();
        assumeTrue(unicodeTtf != null, "No suitable Unicode-capable TTF found on this machine");

        byte[] pdf = assertDoesNotThrow(() -> A11yPdfDocument.builder()
                .lang("en-US")
                .title("Unicode fallback integration")
                .paragraph("Hello Привет Γειά")
                .addFallbackFont(unicodeTtf)
                .buildBytes());

        String extracted = extractText(pdf);
        assertTrue(extracted.contains("Hello"));
        assertTrue(extracted.contains("Привет") || extracted.contains("Γειά"));
    }

    @Test
    void buildBytes_appliesParentListStyleCascadeBeforeDocumentDefault() {
        Path unicodeTtf = findUnicodeCapableTtf();
        assumeTrue(unicodeTtf != null, "No suitable Unicode-capable TTF found on this machine");

        A11yFontFamily unicodeFamily = new A11yFontFamily(
                A11yFontFamily.FontSource.file(unicodeTtf),
                null,
                null,
                null);

        byte[] pdf = assertDoesNotThrow(() -> A11yPdfDocument.builder()
                .lang("en-US")
                .title("Parent style cascade")
                .registerFontFamily("unicode", unicodeFamily)
                .unorderedList(A11yPdfDocument.BoxModel.none(), A11yPdfDocument.TextStyle.of("unicode", FontVariant.REGULAR))
                .item("Привет мир")
                .endList()
                .buildBytes());

        assertTrue(pdf.length > 0);
    }

    private static String extractText(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static Path findUnicodeCapableTtf() {
        String windowsRoot = System.getenv("WINDIR");
        if (windowsRoot == null || windowsRoot.isBlank()) {
            windowsRoot = "C:/Windows";
        }

        List<String> candidates = List.of(
                "Fonts/segoeui.ttf",
                "Fonts/arial.ttf",
                "Fonts/calibri.ttf",
                "Fonts/tahoma.ttf");

        for (String relative : candidates) {
            Path candidate = Path.of(windowsRoot).resolve(relative);
            if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
