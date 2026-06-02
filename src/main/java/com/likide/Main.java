package com.likide;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import com.likide.a11y.pdf.A11yPdfDocument;

public class Main {

    private static final float LEFT = 56.0f;
    private static final float START_Y = 780.0f;
    private static final float LEADING = 16.0f;

    public static void main(String[] args) {
    try {
        Files.createDirectories(Path.of("target"));
        Path output = Path.of("target", "visual-check.pdf");

        byte[] basePdf = A11yPdfDocument.builder()
            .lang("en-US")
            .title("A11y PDF Visual Check")
            .displayDocTitle(true)
            .columns(2, 24.0f)
            .heading(1, "H1: Visual Smoke Check", new A11yPdfDocument.BoxModel(8.0f, 4.0f, 0.0f, 4.0f, 0.0f, 10.0f), 1.3f)
            .paragraph(
                "This paragraph verifies line wrapping and spacing in a visual smoke run. "
                    + "The library generates metadata, structure tags, and a pass-1 layout blueprint.",
                new A11yPdfDocument.BoxModel(0.0f, 2.0f, 2.0f, 2.0f, 2.0f, 8.0f),
                1.4f)
            .unorderedList()
            .item("List Item 1")
            .item("List Item 2")
            .endList()
            .image("demo-figure", "Placeholder figure for visual check", false)
            .artifactHeaderFooter("Page %d of %d")
            .buildBytes();

        byte[] visualPdf = addVisualOverlay(basePdf);
        Files.write(output, visualPdf);

        System.out.println("Visual test PDF generated: " + output.toAbsolutePath());
    } catch (Exception e) {
        throw new IllegalStateException("Failed to generate visual test PDF", e);
    }
    }

    private static byte[] addVisualOverlay(byte[] pdfBytes) throws IOException {
    try (PDDocument document = Loader.loadPDF(pdfBytes)) {
        PDPage page = document.getPage(0);

        try (PDPageContentStream stream = new PDPageContentStream(
            document,
            page,
            PDPageContentStream.AppendMode.APPEND,
            true,
            true)) {
        float y = START_Y;

        y = writeLine(stream, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 18.0f,
            "H1: Visual Smoke Check", LEFT, y);
        y -= 4.0f;
        y = writeLine(stream, new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12.0f,
            "P: This PDF lets you visually inspect currently implemented elements.", LEFT, y);
        y = writeLine(stream, new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12.0f,
            "P: Structure and metadata are from the library build() output.", LEFT, y);
        y -= 10.0f;

        y = writeLine(stream, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 13.0f,
            "List (L / LI / Lbl / LBody)", LEFT, y);
        y = writeLine(stream, new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12.0f,
            "- Item 1", LEFT + 16.0f, y);
        y = writeLine(stream, new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12.0f,
            "- Item 2", LEFT + 16.0f, y);
        y -= 12.0f;

        y = writeLine(stream, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 13.0f,
            "Figure placeholder", LEFT, y);
        float rectX = LEFT;
        float rectY = y - 70.0f;
        float rectW = 240.0f;
        float rectH = 60.0f;
        stream.addRect(rectX, rectY, rectW, rectH);
        stream.stroke();
        writeLine(stream, new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE), 10.0f,
            "FIGURE: Placeholder (alt text set in builder)", rectX + 8.0f, rectY + 36.0f);

        writeLine(stream, new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE), 9.0f,
            "Artifact marker is added by the library when artifactHeaderFooter(...) is set.",
            LEFT,
            60.0f);
        }

        return saveToBytes(document);
    }
    }

    private static float writeLine(PDPageContentStream stream, PDType1Font font, float size, String text, float x, float y)
        throws IOException {
    stream.beginText();
    stream.setFont(font, size);
    stream.newLineAtOffset(x, y);
    stream.showText(text);
    stream.endText();
    return y - LEADING;
    }

    private static byte[] saveToBytes(PDDocument document) throws IOException {
    try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
        document.save(out);
        return out.toByteArray();
    }
    }
}