package com.likide.a11y.pdf;

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
        A11yPdfDocument.BoxModel boxModel = new A11yPdfDocument.BoxModel(4.0f, 6.0f, 8.0f, 10.0f, 12.0f);

        A11yPdfDocument.LayoutBlueprint blueprint = A11yPdfDocument.builder()
                .pageSize(300.0f, 300.0f)
                .pageMargin(20.0f)
                .paragraph("alpha beta gamma delta epsilon zeta", boxModel)
                .layoutBlueprint();

        A11yPdfDocument.LayoutBlock paragraph = blueprint.blocks().get(0);

        assertEquals(boxModel, paragraph.boxModel());
        assertEquals(paragraph.x() + boxModel.paddingLeft(), paragraph.contentX(), 0.0001f);
        assertEquals(paragraph.y() + boxModel.paddingTop(), paragraph.contentY(), 0.0001f);
        assertEquals(paragraph.width() - boxModel.paddingLeft() - boxModel.paddingRight(), paragraph.contentWidth(), 0.0001f);
        assertEquals(paragraph.contentHeight() + boxModel.paddingTop() + boxModel.paddingBottom() + boxModel.marginBottom(), paragraph.height(), 0.0001f);
    }

    @Test
    void layoutBlueprint_shouldFlowAcrossColumnsAndThenPages() {
        A11yPdfDocument.BoxModel boxModel = new A11yPdfDocument.BoxModel(0.0f, 0.0f, 0.0f, 0.0f, 4.0f);

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
    }
}