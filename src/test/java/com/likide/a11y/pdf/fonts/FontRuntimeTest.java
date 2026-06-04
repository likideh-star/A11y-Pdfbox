package com.likide.a11y.pdf.fonts;

import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import org.junit.jupiter.api.Test;

class FontRuntimeTest {

    @Test
    void chunkText_returnsSingleChunkForBasicAnsiText() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            FontRuntime runtime = FontRuntime.load(doc, A11yFontFamily.helvetica(), List.of());

            List<FontRuntime.FontChunk> chunks = runtime.chunkText("Hello World", FontVariant.REGULAR);

            assertEquals(1, chunks.size());
            assertEquals("Hello World", chunks.get(0).text());
            assertSame(runtime.resolveVariant(FontVariant.REGULAR), chunks.get(0).font());
        }
    }

    @Test
    void resolveVariant_fallsBackToRegularWhenBoldMissing() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            A11yFontFamily family = new A11yFontFamily(
                    A11yFontFamily.FontSource.standard14(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),
                    null,
                    null,
                    null);
            FontRuntime runtime = FontRuntime.load(doc, family, List.of());

            assertSame(runtime.resolveVariant(FontVariant.REGULAR), runtime.resolveVariant(FontVariant.BOLD));
            List<FontRuntime.FontChunk> chunks = runtime.chunkText("Bold fallback", FontVariant.BOLD);
            assertEquals(1, chunks.size());
            assertSame(runtime.resolveVariant(FontVariant.REGULAR), chunks.get(0).font());
        }
    }

    @Test
    void chunkText_handlesEmptyInput() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            FontRuntime runtime = FontRuntime.load(doc, A11yFontFamily.helvetica(), List.of());

            List<FontRuntime.FontChunk> chunks = runtime.chunkText("", FontVariant.ITALIC);

            assertEquals(1, chunks.size());
            assertEquals("", chunks.get(0).text());
            assertNotNull(chunks.get(0).font());
        }
    }
}
