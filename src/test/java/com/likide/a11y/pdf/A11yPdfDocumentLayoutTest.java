package com.likide.a11y.pdf;

import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class A11yPdfDocumentLayoutTest {

    @Test
    void layoutBlueprint_shouldWrapParagraphAndUseLineHeightFormula() {
        A11yPdfDocument.LayoutBlueprint blueprint = A11yPdfDocument.builder()
                .pageSize(240.0f, 400.0f)
                .pageMargin(20.0f)
                .paragraph("one two three four five six seven eight nine ten eleven twelve")
                .layoutBlueprint();

        assertEquals(1, blueprint.blocks().size());

        A11yPdfDocument.LayoutBlock paragraph = blueprint.blocks().get(0);
        assertEquals("P", paragraph.role());
        assertTrue(paragraph.lines().size() > 1);
        assertEquals(paragraph.fontSize() * 1.2f, paragraph.lineHeight(), 0.0001f);
        assertEquals(1.2f, paragraph.lineHeightMultiplier(), 0.0001f);
        assertEquals(paragraph.lines().size() * paragraph.lineHeight(), paragraph.contentHeight(), 0.0001f);
        assertEquals(paragraph.contentHeight(), paragraph.height(), 0.0001f);
    }

    @Test
    void layoutBlueprint_shouldKeepHeadingWithNextParagraphAcrossPages() {
        A11yPdfDocument.LayoutBlueprint blueprint = A11yPdfDocument.builder()
                .pageSize(220.0f, 120.0f)
                .pageMargin(20.0f)
                .paragraph("word word word word word word word word word word word word word word word word word word word word")
                .heading(1, "Section heading")
                .paragraph("short body")
                .layoutBlueprint();

        assertEquals(3, blueprint.blocks().size());

        A11yPdfDocument.LayoutBlock firstParagraph = blueprint.blocks().get(0);
        A11yPdfDocument.LayoutBlock heading = blueprint.blocks().get(1);
        A11yPdfDocument.LayoutBlock secondParagraph = blueprint.blocks().get(2);

        assertEquals(0, firstParagraph.pageIndex());
        assertEquals(1, heading.pageIndex());
        assertEquals(1, secondParagraph.pageIndex());
        assertTrue(heading.keepWithNext());
    }

    @Test
    void layoutBlueprint_shouldResolveExplicitBoxModelForParagraph() {
        A11yPdfDocument.BoxModel boxModel = new A11yPdfDocument.BoxModel(3.0f, 4.0f, 6.0f, 8.0f, 10.0f, 12.0f);

        A11yPdfDocument.LayoutBlueprint blueprint = A11yPdfDocument.builder()
                .pageSize(300.0f, 300.0f)
                .pageMargin(20.0f)
                .paragraph("alpha beta gamma delta epsilon zeta", boxModel)
                .layoutBlueprint();

        A11yPdfDocument.LayoutBlock paragraph = blueprint.blocks().get(0);

        assertEquals(boxModel, paragraph.boxModel());
        assertEquals(paragraph.x() + boxModel.paddingLeft(), paragraph.contentX(), 0.0001f);
        assertEquals(paragraph.y() + boxModel.marginTop() + boxModel.paddingTop(), paragraph.contentY(), 0.0001f);
        assertEquals(paragraph.width() - boxModel.paddingLeft() - boxModel.paddingRight(), paragraph.contentWidth(), 0.0001f);
        assertEquals(paragraph.contentHeight() + boxModel.marginTop() + boxModel.paddingTop() + boxModel.paddingBottom() + boxModel.marginBottom(), paragraph.height(), 0.0001f);
    }

    @Test
    void layoutBlueprint_shouldResolveExplicitBoxModelForHeading() {
        A11yPdfDocument.BoxModel boxModel = new A11yPdfDocument.BoxModel(2.0f, 3.0f, 5.0f, 7.0f, 11.0f, 13.0f);

        A11yPdfDocument.LayoutBlueprint blueprint = A11yPdfDocument.builder()
                .pageSize(300.0f, 300.0f)
                .pageMargin(20.0f)
                .heading(2, "heading with explicit box", boxModel)
                .layoutBlueprint();

        A11yPdfDocument.LayoutBlock heading = blueprint.blocks().get(0);

        assertEquals(boxModel, heading.boxModel());
        assertEquals(heading.x() + boxModel.paddingLeft(), heading.contentX(), 0.0001f);
        assertEquals(heading.y() + boxModel.marginTop() + boxModel.paddingTop(), heading.contentY(), 0.0001f);
        assertEquals(heading.width() - boxModel.paddingLeft() - boxModel.paddingRight(), heading.contentWidth(), 0.0001f);
        assertEquals(heading.contentHeight() + boxModel.marginTop() + boxModel.paddingTop() + boxModel.paddingBottom() + boxModel.marginBottom(), heading.height(), 0.0001f);
    }

    @Test
    void layoutBlueprint_shouldFlowAcrossColumnsAndThenPages() {
        A11yPdfDocument.BoxModel boxModel = new A11yPdfDocument.BoxModel(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 4.0f);

        A11yPdfDocument.LayoutBlueprint blueprint = A11yPdfDocument.builder()
                .pageSize(220.0f, 70.0f)
                .pageMargin(20.0f)
                .columns(2, 10.0f)
                .paragraph("a", boxModel)
                .paragraph("b", boxModel)
                .paragraph("c", boxModel)
                .paragraph("d", boxModel)
                .paragraph("e", boxModel)
                .layoutBlueprint();

        assertEquals(5, blueprint.blocks().size());
        assertEquals(3, blueprint.pageCount());

        assertEquals(0, blueprint.blocks().get(0).pageIndex());
        assertEquals(0, blueprint.blocks().get(0).columnIndex());
        assertEquals(0, blueprint.blocks().get(1).pageIndex());
        assertEquals(1, blueprint.blocks().get(1).columnIndex());
        assertEquals(1, blueprint.blocks().get(2).pageIndex());
        assertEquals(0, blueprint.blocks().get(2).columnIndex());
        assertEquals(1, blueprint.blocks().get(3).pageIndex());
        assertEquals(1, blueprint.blocks().get(3).columnIndex());
        assertEquals(2, blueprint.blocks().get(4).pageIndex());
        assertEquals(0, blueprint.blocks().get(4).columnIndex());
        assertTrue(blueprint.diagnostics().stream().anyMatch(line -> line.startsWith("advance ")));
    }

    @Test
    void layoutBlueprint_shouldExposeCursorDiagnosticsForDenseFlow() {
        A11yPdfDocument.LayoutBlueprint blueprint = A11yPdfDocument.builder()
                .pageSize(220.0f, 70.0f)
                .pageMargin(20.0f)
                .columns(2, 8.0f)
                .paragraph("a", new A11yPdfDocument.BoxModel(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 4.0f))
                .paragraph("b", new A11yPdfDocument.BoxModel(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 4.0f))
                .paragraph("c", new A11yPdfDocument.BoxModel(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 4.0f))
                .paragraph("d", new A11yPdfDocument.BoxModel(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 4.0f))
                .paragraph("e", new A11yPdfDocument.BoxModel(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 4.0f))
                .layoutBlueprint();

        assertTrue(blueprint.diagnostics().stream().anyMatch(line -> line.startsWith("place P page=0 column=0")));
        assertTrue(blueprint.diagnostics().stream().anyMatch(line -> line.startsWith("advance ")));
    }

    @Test
    void layoutBlueprint_shouldApplyCustomLineHeightPerElement() {
        A11yPdfDocument.LayoutBlueprint blueprint = A11yPdfDocument.builder()
                .pageSize(260.0f, 300.0f)
                .pageMargin(20.0f)
                .heading(2, "Custom heading", 1.5f)
                .paragraph("custom line height paragraph example", 1.8f)
                .layoutBlueprint();

        A11yPdfDocument.LayoutBlock heading = blueprint.blocks().get(0);
        A11yPdfDocument.LayoutBlock paragraph = blueprint.blocks().get(1);

        assertEquals(1.5f, heading.lineHeightMultiplier(), 0.0001f);
        assertEquals(heading.fontSize() * 1.5f, heading.lineHeight(), 0.0001f);

        assertEquals(1.8f, paragraph.lineHeightMultiplier(), 0.0001f);
        assertEquals(paragraph.fontSize() * 1.8f, paragraph.lineHeight(), 0.0001f);
    }

    @Test
    void buildBytes_textOnlyFlow_shouldUseBlueprintPageCountDeterministically() throws IOException {
        A11yPdfDocument.Builder builder = A11yPdfDocument.builder()
                .pageSize(220.0f, 120.0f)
                .pageMargin(20.0f)
                .heading(1, "A heading")
                .paragraph("word word word word word word word word word word word word word word word word word word")
                .paragraph("word word word word word word word word word word word word word word word word word word")
                .paragraph("word word word word word word word word word word word word word word word word word word");

        A11yPdfDocument.LayoutBlueprint blueprint = builder.layoutBlueprint();
        byte[] pdf = builder.buildBytes();

        try (PDDocument rendered = Loader.loadPDF(pdf)) {
            assertEquals(blueprint.pageCount(), rendered.getNumberOfPages());
        }
    }

    @Test
    void buildBytes_mixedFlowLongParagraph_shouldPaginateInsteadOfClipping() throws IOException {
        String longText = "long flow text ".repeat(1500);

        byte[] pdf = A11yPdfDocument.builder()
                .pageSize(220.0f, 160.0f)
                .pageMargin(20.0f)
                .columns(2, 8.0f)
                .heading(2, "Long mixed-flow section")
                .paragraph(longText)
                .tableOfContents("Outline", 2)
                .buildBytes();

        try (PDDocument rendered = Loader.loadPDF(pdf)) {
            assertTrue(rendered.getNumberOfPages() > 2);
        }
    }

    @Test
    void buildBytes_longListItem_shouldWrapAndPreserveTailText() throws IOException {
        String longItem = "segment".repeat(400) + " TAIL_MARKER";

        byte[] pdf = A11yPdfDocument.builder()
                .pageSize(220.0f, 180.0f)
                .pageMargin(20.0f)
                .columns(2, 8.0f)
            .heading(2, "List Section")
                .unorderedList()
                .item(longItem)
                .item("short item")
                .endList()
                .tableOfContents("Outline", 2)
                .buildBytes();

        try (PDDocument rendered = Loader.loadPDF(pdf)) {
            String extracted = new PDFTextStripper().getText(rendered);
            assertTrue(extracted.contains("TAIL_MARKER"));
        }
    }

    @Test
    void buildBytes_longListItemWithAlignWithBulletIndent_shouldWrapAndPreserveTailText() throws IOException {
        String longItem = "segment".repeat(400) + " ALIGN_TAIL";

        byte[] pdf = A11yPdfDocument.builder()
                .pageSize(220.0f, 180.0f)
                .pageMargin(20.0f)
                .columns(2, 8.0f)
                .heading(2, "List Section")
                .unorderedList(A11yPdfDocument.BoxModel.none(), A11yPdfDocument.TextStyle.none(), A11yPdfDocument.ListIndentStyle.ALIGN_WITH_BULLET, 0.0f)
                .item(longItem)
                .endList()
                .tableOfContents("Outline", 2)
                .buildBytes();

        try (PDDocument rendered = Loader.loadPDF(pdf)) {
            String extracted = new PDFTextStripper().getText(rendered);
            assertTrue(extracted.contains("ALIGN_TAIL"));
        }
    }

    @Test
    void buildBytes_longListItemWithCustomIndent_shouldWrapAndPreserveTailText() throws IOException {
        String longItem = "segment".repeat(400) + " CUSTOM_TAIL";

        byte[] pdf = A11yPdfDocument.builder()
                .pageSize(220.0f, 180.0f)
                .pageMargin(20.0f)
                .columns(2, 8.0f)
                .heading(2, "List Section")
                .unorderedList(A11yPdfDocument.BoxModel.none(), A11yPdfDocument.TextStyle.none(), A11yPdfDocument.ListIndentStyle.CUSTOM, 20.0f)
                .item(longItem)
                .endList()
                .tableOfContents("Outline", 2)
                .buildBytes();

        try (PDDocument rendered = Loader.loadPDF(pdf)) {
            String extracted = new PDFTextStripper().getText(rendered);
            assertTrue(extracted.contains("CUSTOM_TAIL"));
        }
    }
}