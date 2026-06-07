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
    private static final float RIGHT = 320.0f;
    private static final float START_Y = 780.0f;
    private static final float LEADING = 16.0f;

    public static void main(String[] args) {
        try {
            Files.createDirectories(Path.of("target"));
            Path output = Path.of("target", "visual-check.pdf");

            byte[] basePdf = A11yPdfDocument.builder()
                    .lang("en-US")
                    .title("A11y PDF Visual Check")
                    .artifactHeaderText("Company Confidential", A11yPdfDocument.ChromeAlignment.LEFT)
                    .artifactHeaderLink("https://example.com", "https://example.com", A11yPdfDocument.ChromeAlignment.RIGHT)
                    .artifactHeaderImage("src/main/resources/examples/coq.png", 36f, 20f, A11yPdfDocument.ChromeAlignment.CENTER)
                    .artifactFooterText("Internal Use Only", A11yPdfDocument.ChromeAlignment.LEFT)
                    .artifactPageNumber("Page %d of %d", A11yPdfDocument.PageNumberAlignment.ALTERNATE)
                    .displayDocTitle(true)
                    .columns(2, 24.0f)
                    .heading(1, "H1: Visual Smoke Check", new A11yPdfDocument.BoxModel(8.0f, 4.0f, 0.0f, 4.0f, 0.0f, 10.0f), 1.3f)
                    .paragraph(
                            "This run includes heading, paragraph, list, figure, table, TOC, and custom-node samples. "
                                    + "Use this file for quick visual and structure sanity checks.",
                            new A11yPdfDocument.BoxModel(0.0f, 2.0f, 2.0f, 2.0f, 2.0f, 8.0f),
                            1.4f)
                    .heading(2, "Section A")
                    .paragraph("A paragraph under Section A.")
                    .heading(2, "Section B")
                    .paragraph("A paragraph under Section B.")
                    .unorderedList()
                    .item("List Item 1")
                    .item("List Item 2")
                    .endList()
                    .table()
                    .headerCell("Name")
                    .headerCell("Value")
                    .row()
                    .cell("Rows")
                    .cell("2")
                    .endRow()
                    .row()
                    .cell("Status")
                    .cell("OK")
                    .endRow()
                    .endTable()
                    .tableOfContents("Document Outline", 2)
                    .customNode("future.analytics", "KpiWidget")
                    .attribute("metric", "views")
                    .attribute("timeRange", "30d")
                    .endCustomNode()
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
            PDPage overviewPage = document.getPage(0);
            PDPage detailsPage = new PDPage(overviewPage.getMediaBox());
            document.addPage(detailsPage);

            try (PDPageContentStream stream = new PDPageContentStream(
                    document,
                    overviewPage,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true)) {
                drawOverviewOverlay(stream);
            }

            try (PDPageContentStream stream = new PDPageContentStream(
                    document,
                    detailsPage,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true)) {
                drawDedicatedOverlay(stream);
            }

            return saveToBytes(document);
        }
    }

    private static void drawOverviewOverlay(PDPageContentStream stream) throws IOException {
        PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font italic = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

        float y = START_Y;

        y = writeLine(stream, bold, 18.0f, "H1: Visual Smoke Check", LEFT, y);
        y -= 4.0f;
        y = writeLine(stream, regular, 11.0f, "P: Left side shows heading, paragraph, list, figure.", LEFT, y);
        y = writeLine(stream, regular, 11.0f, "P: Right side shows TOC, table, and custom node example.", LEFT, y);

        y -= 8.0f;
        y = writeLine(stream, bold, 13.0f, "List (L / LI / Lbl / LBody)", LEFT, y);
        y = writeLine(stream, regular, 11.0f, "- Item 1", LEFT + 16.0f, y);
        y = writeLine(stream, regular, 11.0f, "- Item 2", LEFT + 16.0f, y);

        y -= 8.0f;
        y = writeLine(stream, bold, 13.0f, "Figure placeholder (Figure)", LEFT, y);
        drawFigurePlaceholder(stream, LEFT, y - 8.0f, 230.0f, 68.0f, "FIGURE: Placeholder (alt text defined in builder)");

        float tocY = START_Y - 30.0f;
        writeLine(stream, bold, 13.0f, "TOC (TOC)", RIGHT, tocY);
        writeLine(stream, regular, 11.0f, "1  Section A ........................................ 2", RIGHT, tocY - LEADING);
        writeLine(stream, regular, 11.0f, "2  Section B ........................................ 3", RIGHT, tocY - (2 * LEADING));

        float tableTop = tocY - 60.0f;
        writeLine(stream, bold, 13.0f, "Table (Table)", RIGHT, tableTop);
        drawTable(stream, RIGHT, tableTop - 10.0f);

        float customY = tableTop - 130.0f;
        writeLine(stream, bold, 13.0f, "Custom node (Sect / family=future.analytics)", RIGHT, customY);
        writeLine(stream, italic, 10.0f, "type=KpiWidget, metric=views, timeRange=30d", RIGHT + 10.0f, customY - 14.0f);

        writeLine(stream, italic, 9.0f,
                "Artifact marker is added by the library when artifactHeaderFooter(...) is set.",
                LEFT,
                60.0f);
    }

    private static void drawDedicatedOverlay(PDPageContentStream stream) throws IOException {
        PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font italic = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

        float y = START_Y;
        y = writeLine(stream, bold, 17.0f, "Page 2: Dedicated Element Visuals", LEFT, y);
        y = writeLine(stream, regular, 11.0f, "Each block below represents one currently supported element family.", LEFT, y);

        y -= 4.0f;
        y = writeLine(stream, bold, 13.0f, "TOC block", LEFT, y);
        y = writeLine(stream, regular, 11.0f, "1  H1 Visual Smoke Check ............................. 1", LEFT + 12.0f, y);
        y = writeLine(stream, regular, 11.0f, "2  Section A .......................................... 1", LEFT + 12.0f, y);
        y = writeLine(stream, regular, 11.0f, "3  Section B .......................................... 1", LEFT + 12.0f, y);

        y -= 6.0f;
        y = writeLine(stream, bold, 13.0f, "Table block", LEFT, y);
        drawTable(stream, LEFT, y - 8.0f);

        float figureTop = y - 142.0f;
        writeLine(stream, bold, 13.0f, "Figure block", LEFT, figureTop);
        drawFigurePlaceholder(stream, LEFT, figureTop - 8.0f, 300.0f, 86.0f, "FIGURE: Visual frame + caption sample");

        float listY = figureTop - 126.0f;
        writeLine(stream, bold, 13.0f, "List block", LEFT, listY);
        writeLine(stream, regular, 11.0f, "- Item 1", LEFT + 12.0f, listY - LEADING);
        writeLine(stream, regular, 11.0f, "- Item 2", LEFT + 12.0f, listY - (2 * LEADING));

        float customY = listY - 70.0f;
        writeLine(stream, bold, 13.0f, "Custom node block", LEFT, customY);
        writeLine(stream, italic, 10.0f, "Sect: family=future.analytics, type=KpiWidget", LEFT + 12.0f, customY - LEADING);
        writeLine(stream, italic, 10.0f, "attributes: metric=views, timeRange=30d", LEFT + 12.0f, customY - (2 * LEADING));
    }

    private static void drawFigurePlaceholder(PDPageContentStream stream, float x, float yTop, float width, float height, String caption)
            throws IOException {
        float rectY = yTop - height;
        stream.addRect(x, rectY, width, height);
        stream.stroke();
        writeLine(stream, new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE), 10.0f, caption, x + 8.0f, rectY + (height / 2.0f));
    }

    private static void drawTable(PDPageContentStream stream, float x, float yTop) throws IOException {
        float width = 220.0f;
        float rowHeight = 18.0f;
        float headerY = yTop - rowHeight;
        float row1Y = headerY - rowHeight;
        float row2Y = row1Y - rowHeight;

        stream.addRect(x, row2Y, width, rowHeight * 3.0f);
        stream.moveTo(x, headerY);
        stream.lineTo(x + width, headerY);
        stream.moveTo(x, row1Y);
        stream.lineTo(x + width, row1Y);
        stream.moveTo(x + (width / 2.0f), yTop);
        stream.lineTo(x + (width / 2.0f), row2Y);
        stream.stroke();

        writeLine(stream, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 10.0f, "Name", x + 6.0f, yTop - 13.0f);
        writeLine(stream, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 10.0f, "Value", x + (width / 2.0f) + 6.0f, yTop - 13.0f);
        writeLine(stream, new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10.0f, "Rows", x + 6.0f, headerY - 13.0f);
        writeLine(stream, new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10.0f, "2", x + (width / 2.0f) + 6.0f, headerY - 13.0f);
        writeLine(stream, new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10.0f, "Status", x + 6.0f, row1Y - 13.0f);
        writeLine(stream, new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10.0f, "OK", x + (width / 2.0f) + 6.0f, row1Y - 13.0f);
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